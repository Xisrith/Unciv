package com.unciv.ui.screens.worldscreen.bottombar

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.widgets.UnitIconGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.view.ForeignMapUnitView
import com.unciv.view.TileView

/**
 * Controller layout: a card in the bottom-left corner describing the units on the tile under the
 * gamepad reticle (name, owner, health, strength, what they're doing). Shown only while nothing is
 * selected - with a selection, the selected unit/city card takes its place.
 *
 * Unit lines are removed from the tile info box in controller mode, this card shows them instead.
 */
class GamepadUnitHoverCard(private val worldScreen: WorldScreen) : Table(BaseScreen.skin) {

    init {
        isVisible = false
        touchable = Touchable.disabled
        background = BaseScreen.skinStrings.getUiBackground(
            "WorldScreen/TileInfoTable",
            tintColor = BaseScreen.skinStrings.skinConfig.baseColor.darken(0.5f)
        )
        pad(8f)
        defaults().left()
    }

    /** Show the visible units on [tileView]; hides when there are none (or [show] is false) */
    fun update(tileView: TileView?, show: Boolean) {
        clear()
        val units = if (show && tileView != null) tileView.getVisibleUnits() else emptyList()
        if (units.isEmpty()) {
            isVisible = false
            return
        }
        // Military first - it's the one you'd fight or that's guarding the tile
        val sorted = units.sortedBy { if (it.getBaseUnit().isMilitary) 0 else 1 }
        for ((index, unit) in sorted.withIndex())
            add(getUnitRow(unit)).padTop(if (index == 0) 0f else 8f).row()
        isVisible = true
        pack()
    }

    private fun getUnitRow(unit: ForeignMapUnitView) = Table().apply {
        defaults().left()
        add(UnitIconGroup(unit, 36f)).top().padRight(8f)

        val texts = Table()
        texts.defaults().left()
        texts.add(unit.displayName().toLabel(fontSize = 18)).row()
        texts.add(unit.civName.toLabel(Color.LIGHT_GRAY, 14)).row()

        val stats = ArrayList<String>()
        val base = unit.getBaseUnit()
        if (unit.unitHealth < 100) stats += "${Fonts.health}${unit.unitHealth}"
        if (base.strength > 0) stats += "${Fonts.strength}${base.strength}"
        if (base.rangedStrength > 0) stats += "${Fonts.rangedStrength}${base.rangedStrength}"
        val ownUnit = unit.tryGetMapUnitView()
        if (ownUnit != null) stats += ownUnit.getMovementString()
        else stats += "${Fonts.movement}${unit.getMaxMovement()}"
        if (stats.isNotEmpty()) texts.add(stats.joinToString("  ").toLabel(fontSize = 14)).row()

        getStatus(unit)?.let { texts.add(it.toLabel(Color.GOLD, 14)).row() }
        getWorkInProgress(unit)?.let { texts.add(it).row() }
        add(texts)
    }

    /** A civilian on a tile with an improvement under construction is the one building it:
     *  `[farm icon] Farm - 2⏳` (the tile info leaves this out in controller mode) */
    private fun getWorkInProgress(unit: ForeignMapUnitView): Table? {
        if (unit.getBaseUnit().isMilitary) return null
        val tile = unit.getTile()
        val improvement = tile.improvementInProgress ?: return null
        val turns = tile.turnsToImprovement
        // Negative turnsToImprovement is used for UniqueType.CreatesOneImprovement
        val text = improvement.tr() +
            if (turns > 0) " - $turns${Fonts.turn}" else " " + "({Under construction})".tr()
        return Table().apply {
            add(ImageGetter.getImprovementPortrait(improvement, 18f)).padRight(5f)
            add(text.toLabel(Color.GOLD, 14))
        }
    }

    /** "What it's doing" - full detail for own units, only what's visible (fortified) for others */
    private fun getStatus(unit: ForeignMapUnitView): String? {
        val ownUnit = unit.tryGetMapUnitView()
        if (ownUnit != null) {
            val action = ownUnit.action
            return when {
                ownUnit.isAutomated() -> "Automated".tr()
                action == null -> if (ownUnit.isIdle()) "Idle".tr() else null
                action.startsWith("moveTo") -> "Moving".tr()
                action.startsWith("Fortify") -> "Fortified".tr()
                else -> action.substringBefore(' ').tr()
            }
        }
        return when {
            unit.isFortified() -> "Fortified".tr()
            unit.isGuarding() -> "Guarding".tr()
            else -> null
        }
    }
}
