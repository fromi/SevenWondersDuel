package fr.omi.sevenwondersduel

import fr.omi.sevenwondersduel.Structure.Companion.age3
import fr.omi.sevenwondersduel.effects.*
import fr.omi.sevenwondersduel.effects.victorypoints.VictoryPointsEffect
import fr.omi.sevenwondersduel.material.*
import kotlin.math.abs
import kotlin.math.absoluteValue

data class SevenWondersDuel(val players: Pair<Player, Player> = Pair(Player(), Player()),
                            val conflictPawnPosition: Int = 0,
                            val progressTokensAvailable: Set<ProgressToken> = ProgressToken.values().toList().shuffled().asSequence().take(5).toSet(),
                            val currentPlayerNumber: Int? = 1,
                            val wondersAvailable: Set<Wonder> = wonders.shuffled().take(4).toSet(),
                            val structure: Structure? = null,
                            val discardedCards: List<Building> = emptyList(),
                            val pendingActions: List<PendingAction> = emptyList()) {

    fun take(wonder: Wonder): SevenWondersDuel {
        var game = give(currentPlayer, wonder)
        if (game.wondersAvailable.size != 2)
            game = game.swapCurrentPlayer()
        if (game.players.toList().all { it.wonders.size == 4 })
            game = game.copy(structure = Structure(age = 1))
        else if (game.wondersAvailable.isEmpty())
            game = game.copy(wondersAvailable = remainingWonders().shuffled().asSequence().take(4).toSet())
        return game
    }

    fun construct(building: Building): SevenWondersDuel {
        val game = if (pendingActions.firstOrNull() == DiscardedBuildingToBuild) {
            require(discardedCards.contains(building))
            copy(discardedCards = discardedCards.minus(building), pendingActions = pendingActions.drop(1))
        } else {
            takeAndPay(building)
        }
        return game.currentPlayerDo { it.construct(building) }
                .applyEffects(building)
                .continueGame()
    }

    private fun takeAndPay(building: Building): SevenWondersDuel {
        val game = take(building)
        return if (currentPlayer.buildings.contains(building.freeLink)) {
            game.applyEffects(currentPlayer.effects.filterIsInstance<ChainBuildingTriggeredEffect>().map { it.triggeredEffect }.toList())
        } else {
            game.pay(building)
        }
    }

    fun discard(building: Building): SevenWondersDuel =
            take(building)
                    .currentPlayerDo { it.discard() }
                    .copy(discardedCards = discardedCards.plus(building))
                    .continueGame()

    fun construct(wonder: Wonder, buildingUsed: Building): SevenWondersDuel =
            take(buildingUsed)
                    .pay(wonder)
                    .currentPlayerDo { it.construct(wonder, buildingUsed) }
                    .applyEffects(wonder)
                    .discardIf7WondersBuilt()
                    .continueGame()

    fun choosePlayerBeginningNextAge(player: Int): SevenWondersDuel {
        check(pendingActions.firstOrNull() is PlayerBeginningAgeToChoose)
        return copy(currentPlayerNumber = player, pendingActions = pendingActions.drop(1))
    }

    fun choose(progressToken: ProgressToken): SevenWondersDuel {
        val chooseProgressToken = checkNotNull(pendingActions.firstOrNull() as? ProgressTokenToChoose) { "You are not currently allowed to choose a progress token" }
        require(chooseProgressToken.tokens.contains(progressToken)) { "You cannot choose this progress token" }
        return currentPlayerDo { it.take(progressToken) }
                .copy(progressTokensAvailable = progressTokensAvailable.minus(progressToken),
                        pendingActions = pendingActions.minus(chooseProgressToken))
                .applyEffects(progressToken.effects)
                .continueGame()
    }

    fun destroy(building: Building): SevenWondersDuel {
        check(pendingActions.isNotEmpty() && pendingActions.first() is OpponentBuildingToDestroy) { "You are not currently allowed to destroy an opponent building" }
        val action = pendingActions.first() as OpponentBuildingToDestroy
        require(action.isEligible(building)) { "You are not allowed to destroy this kind of building" }
        return pairInOrder(currentPlayer, opponent.destroy(building))
                .copy(pendingActions = pendingActions.minus(action), discardedCards = discardedCards.plus(building))
                .continueGame()
    }

    val currentPlayer: Player
        get() = when (currentPlayerNumber) {
            1 -> players.first
            2 -> players.second
            else -> throw IllegalStateException("Game is over")
        }

    val opponent: Player
        get() = when (currentPlayerNumber) {
            1 -> players.second
            2 -> players.first
            else -> throw IllegalStateException("Game is over")
        }

    private fun swapCurrentPlayer(): SevenWondersDuel = copy(currentPlayerNumber = when (currentPlayerNumber) {
        1 -> 2
        2 -> 1
        else -> throw IllegalStateException("Game is over")
    })

    private fun currentPlayerDo(action: (Player) -> Player): SevenWondersDuel = pairInOrder(action(currentPlayer), opponent)

    fun pairInOrder(player: Player, opponent: Player): SevenWondersDuel =
            copy(players = when (currentPlayerNumber) {
                1 -> Pair(first = player, second = opponent)
                2 -> Pair(first = opponent, second = player)
                else -> throw IllegalStateException("Game is over")
            })

    private fun give(player: Player, wonder: Wonder): SevenWondersDuel {
        require(wondersAvailable.contains(wonder)) { "This wonder is not available" }
        val players = when (player) {
            players.first -> players.copy(first = player.take(wonder))
            players.second -> players.copy(second = player.take(wonder))
            else -> throw IllegalArgumentException("This player is not in this game")
        }
        return copy(wondersAvailable = wondersAvailable.minus(wonder), players = players)
    }

    private fun remainingWonders() = wonders.filter { wonder -> !wondersAvailable.contains(wonder) && players.toList().none { player -> player.wonders.any { it.wonder == wonder } } }

    private fun prepareNextAge(): SevenWondersDuel {
        val playerChoosingWhoBeginsNextAge = when {
            conflictPawnPosition < 0 -> 1
            conflictPawnPosition > 0 -> 2
            else -> currentPlayerNumber
        }
        return copy(structure = Structure(age = checkNotNull(structure).age.inc()), currentPlayerNumber = playerChoosingWhoBeginsNextAge, pendingActions = listOf(PlayerBeginningAgeToChoose))
    }

    private fun take(building: Building): SevenWondersDuel {
        check(pendingActions.isEmpty()) { "At least one pending action must be done before a new building can be taken" }
        return copy(structure = checkNotNull(structure).take(building))
    }

    private fun pay(construction: Construction): SevenWondersDuel {
        val player = currentPlayer.pay(construction, ::getTradingCost)
        val opponent = if (opponent.effects.any { it is GainTradingCost })
            opponent.takeCoins(currentPlayer.sumTradingCost(construction, ::getTradingCost))
        else opponent
        return pairInOrder(player, opponent)
    }

    fun coinsToPay(construction: Construction): Int =
            if (construction is Building && currentPlayer.buildings.contains(construction.freeLink)) 0
            else construction.cost.coins + currentPlayer.sumTradingCost(construction, ::getTradingCost)

    private fun getTradingCost(resource: Resource): Int =
            if (currentPlayer.effects.any { it is FixTradingCostTo1 && it.resource == resource }) 1 else 2 + opponent.productionOf(resource)

    private fun continueGame(): SevenWondersDuel {
        return when {
            isOver -> copy(currentPlayerNumber = null)
            pendingActions.isNotEmpty() -> if (pendingActions.first() == PlayAgain) playAgain() else this
            currentAgeIsOver -> prepareNextAge()
            else -> nextPlayer()
        }
    }

    private fun playAgain() = copy(pendingActions = pendingActions.drop(1), structure = checkNotNull(structure).revealAccessibleBuildings())

    private fun nextPlayer() = copy(currentPlayerNumber = if (currentPlayerNumber == 1) 2 else 1, structure = checkNotNull(structure).revealAccessibleBuildings())

    val currentAgeIsOver: Boolean get() = checkNotNull(structure).isEmpty()

    private fun applyEffects(construction: Construction): SevenWondersDuel {
        val triggeredEffects = currentPlayer.effects.filterIsInstance<ConstructionTriggeredEffect>().filter { it.appliesTo(construction) }.map { it.triggeredEffect }
        return applyEffects(construction.effects.plus(triggeredEffects))
    }

    private fun applyEffects(effects: Collection<Effect>): SevenWondersDuel {
        return if (effects.isEmpty()) this else effects.first().applyTo(this).applyEffects(effects.drop(1))
    }

    private fun discardIf7WondersBuilt(): SevenWondersDuel =
            if (players.toList().sumBy { player -> player.wonders.count { it.isConstructed() } } == 7)
                copy(players = players.copy(players.first.discardUnfinishedWonder(), players.second.discardUnfinishedWonder()))
            else this

    fun moveConflictPawn(quantity: Int): SevenWondersDuel {
        val newConflictPosition = if (currentPlayerNumber == 1) minOf(conflictPawnPosition + quantity, 9) else maxOf(conflictPawnPosition - quantity, -9)
        return if (newConflictPosition.absoluteValue >= 9) copy(conflictPawnPosition = newConflictPosition, currentPlayerNumber = null)
        else copy(conflictPawnPosition = newConflictPosition).lootMilitaryTokens()
    }

    private fun lootMilitaryTokens(): SevenWondersDuel {
        return when (conflictPawnPosition) {
            in -8..-6 -> if (players.first.militaryTokensLooted < 2) copy(players = players.copy(players.first.lootSecondToken(), players.second)) else this
            in -5..-3 -> if (players.first.militaryTokensLooted < 1) copy(players = players.copy(players.first.lootFirstToken(), players.second)) else this
            in 3..5 -> if (players.second.militaryTokensLooted < 1) copy(players = players.copy(players.first, players.second.lootFirstToken())) else this
            in 6..8 -> if (players.second.militaryTokensLooted < 2) copy(players = players.copy(players.first, players.second.lootSecondToken())) else this
            else -> this
        }
    }

    fun takeCoins(quantity: Int): SevenWondersDuel {
        return pairInOrder(currentPlayer.takeCoins(quantity), opponent)
    }

    val isOver: Boolean
        get() = abs(conflictPawnPosition) == 9
                || players.first.hasScientificSupremacy || players.second.hasScientificSupremacy
                || structure != null && structure.age == age3 && structure.isEmpty()

    val winner: Player?
        get() = when {
            conflictPawnPosition >= 9 -> players.first
            conflictPawnPosition <= -9 -> players.second
            players.first.hasScientificSupremacy -> players.first
            players.second.hasScientificSupremacy -> players.second
            else -> getHighest(players, ::countVictoryPoint) ?: getHighest(players, Player::countCivilianBuildingsVictoryPoints)
        }

    private fun getHighest(players: Pair<Player, Player>, selector: (Player) -> Int): Player? {
        val firstValue = selector(players.first)
        val secondValue = selector(players.second)
        return when {
            firstValue > secondValue -> players.first
            firstValue < secondValue -> players.second
            else -> null
        }
    }

    fun countVictoryPoint(player: Player): Int {
        return militaryPoints(player) + player.effects.filterIsInstance<VictoryPointsEffect>().sumBy { it.count(this, player) } + player.coins / 3
    }

    private fun militaryPoints(player: Player): Int {
        val relativeConflictPawnPosition = if (player == players.first) conflictPawnPosition else -conflictPawnPosition
        return when (relativeConflictPawnPosition) {
            in 1..2 -> 2
            in 3..5 -> 5
            in 6..8 -> 10
            else -> 0
        }
    }
}
