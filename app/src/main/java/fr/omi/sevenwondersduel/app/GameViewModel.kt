package fr.omi.sevenwondersduel.app

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import fr.omi.sevenwondersduel.SevenWondersDuel
import fr.omi.sevenwondersduel.ai.ChoosePlayerBeginningAge
import fr.omi.sevenwondersduel.ai.SevenWondersDuelMove
import fr.omi.sevenwondersduel.effects.PlayerBeginningAgeToChoose
import fr.omi.sevenwondersduel.event.Action

class GameViewModel : ViewModel() {
    var game = SevenWondersDuel()
    val actions = MutableLiveData<Action>()

    init {
        //repeat(28) { game = RandomBot.play(game) }
        //game = RandomBot.playUntilMoveAvailable { game, move -> move is ConstructWonder && game.players.toList().sumBy { player -> player.wonders.count { it.isConstructed() } } == 6 }
    }

    fun execute(move: SevenWondersDuelMove) {
        if (move !is ChoosePlayerBeginningAge && game.pendingActions.any { it is PlayerBeginningAgeToChoose })
            game = game.choosePlayerBeginningNextAge(checkNotNull(game.currentPlayerNumber))
        actions.postValue(Action(game, move))
        game = move.applyTo(game)
    }

    fun reset() {
        actions.value = null
        game = SevenWondersDuel()
    }
}