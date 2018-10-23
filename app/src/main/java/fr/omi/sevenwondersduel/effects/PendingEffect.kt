package fr.omi.sevenwondersduel.effects

import fr.omi.sevenwondersduel.SevenWondersDuel

interface PendingEffect : Effect, PendingAction {
    override fun applyTo(game: SevenWondersDuel): SevenWondersDuel {
        return game.copy(pendingActions = game.pendingActions.plus(this))
    }
}