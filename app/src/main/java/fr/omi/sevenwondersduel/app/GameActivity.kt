package fr.omi.sevenwondersduel.app

import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import fr.omi.sevenwondersduel.PlayerWonder
import fr.omi.sevenwondersduel.R
import fr.omi.sevenwondersduel.Structure
import fr.omi.sevenwondersduel.app.state.*
import fr.omi.sevenwondersduel.effects.ProgressTokenToChoose
import fr.omi.sevenwondersduel.event.*
import fr.omi.sevenwondersduel.material.Building
import fr.omi.sevenwondersduel.material.BuildingCard
import fr.omi.sevenwondersduel.material.ProgressToken
import fr.omi.sevenwondersduel.material.Wonder
import kotlinx.android.synthetic.main.activity_game.*

class GameActivity : AppCompatActivity() {

    lateinit var model: GameViewModel
    private val game get() = model.game
    private var state: GameActivityState? = null
    private val wondersViews: MutableMap<Wonder, WonderView> = hashMapOf()
    private val buildingsViews: MutableMap<Building, BuildingView> = hashMapOf()
    private var structureBuildingsViews: List<Map<Int, BuildingView>> = listOf()
    private lateinit var conflictPawnView: ConflictPawnView
    private val progressTokensViews: MutableMap<ProgressToken, ProgressTokenView> = hashMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        model = ViewModelProviders.of(this).get(GameViewModel::class.java)

        game.progressTokensAvailable.forEachIndexed { position, progressToken -> createView(progressToken).availableAt(position) }
        conflictPawnView = ConflictPawnView(this, game.conflictPawnPosition)
        game.wondersAvailable.forEachIndexed(this::createAvailableWonderView)
        game.structure?.let { display(it) }
        game.players.first.apply {
            wonders.forEachIndexed { index, wonder -> createView(wonder, 1, index) }
            buildings.forEachIndexed { index, it -> createView(it).positionForPlayer(1, index) }
            firstPlayerCoins.text = coins.toString()
            if (militaryTokensLooted >= 1) layout.removeView(loot2player1)
            if (militaryTokensLooted >= 2) layout.removeView(loot5player1)
            progressTokens.forEachIndexed { index, progressToken -> createView(progressToken).positionForPlayer(1, index) }
        }
        game.players.second.apply {
            wonders.forEachIndexed { index, wonder -> createView(wonder, 2, index) }
            buildings.forEachIndexed { index, it -> createView(it).positionForPlayer(2, index) }
            secondPlayerCoins.text = coins.toString()
            if (militaryTokensLooted >= 1) layout.removeView(loot2player2)
            if (militaryTokensLooted >= 2) layout.removeView(loot5player2)
            progressTokens.forEachIndexed { index, progressToken -> createView(progressToken).positionForPlayer(2, index) }
        }

        model.actions.observe(this) { action ->
            if (state != null) handle(action)
            updateState()
        }
        updateState()

        resetButton.setOnClickListener {
            model.reset()
            recreate()
        }
    }

    private fun updateState() {
        state?.leave()
        state = when {
            game.wondersAvailable.isNotEmpty() -> TakeWonderState(this)
            game.isOver -> GameOverState(this)
            game.pendingActions.isNotEmpty() && game.pendingActions.first() is ProgressTokenToChoose -> ChooseProgressTokenState(this)
            else -> PlayAccessibleCardState(this)
        }
    }

    private fun handle(action: Action) {
        firstPlayerCoins.text = game.players.first.coins.toString()
        secondPlayerCoins.text = game.players.second.coins.toString()
        if (game.conflictPawnPosition != conflictPawnView.position) {
            conflictPawnView.position = game.conflictPawnPosition
        }
        action.inferEventsLeadingTo(model.game).forEach(::handleEvent)
    }

    private fun handleEvent(event: GameEvent) {
        when (event) {
            is PlaceFourAvailableWondersEvent -> event.wonders.forEachIndexed(this::createAvailableWonderView)
            is PrepareStructureEvent -> display(event.structure)
            is BuildingMadeAccessibleEvent -> getView(event.building).reveal()
            is MilitaryTokenLooted -> when (event.playerNumber) {
                1 -> when (event.tokenNumber) {
                    1 -> layout.removeView(loot2player1)
                    2 -> layout.removeView(loot5player1)
                }
                2 -> when (event.tokenNumber) {
                    1 -> layout.removeView(loot2player2)
                    2 -> layout.removeView(loot5player2)
                }
            }
        }
    }

    private fun createAvailableWonderView(position: Int, wonder: Wonder) {
        createView(wonder).positionInto(0, position)
    }

    private fun createView(playerWonder: PlayerWonder, owner: Int, position: Int) {
        val wonderView = createView(playerWonder.wonder)
        wonderView.positionInto(owner, position)
        if (playerWonder.buildingUnder != null) {
            BuildingView(this, playerWonder.buildingUnder, faceUp = false).positionUnder(wonderView, owner)
        }
    }

    private fun createView(wonder: Wonder): WonderView {
        return WonderView(this, wonder).apply {
            wondersViews[wonder] = this
        }
    }

    private fun createView(progressToken: ProgressToken): ProgressTokenView {
        return ProgressTokenView(this, progressToken).apply {
            progressTokensViews[progressToken] = this
        }
    }

    private fun display(structure: Structure) {
        structureBuildingsViews = structure.mapIndexed { rowIndex, row ->
            row.mapValues { entry -> createView(entry.value, rowIndex, entry.key) }
        }
    }

    private fun createView(buildingCard: BuildingCard, row: Int, column: Int): BuildingView {
        return BuildingView(this, buildingCard).apply {
            buildingsViews[building] = this
            positionInStructure(row, column)
        }
    }

    private fun createView(building: Building): BuildingView {
        return BuildingView(this, building, faceUp = true).apply {
            buildingsViews[building] = this
        }
    }

    fun getView(building: Building): BuildingView {
        return checkNotNull(buildingsViews[building])
    }

    fun remove(buildingView: BuildingView) {
        layout.removeView(buildingView)
    }

    fun getView(wonder: Wonder): WonderView {
        return checkNotNull(wondersViews[wonder])
    }

    fun getView(progressToken: ProgressToken): ProgressTokenView {
        return checkNotNull(progressTokensViews[progressToken])
    }
}
