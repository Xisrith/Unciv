package com.unciv.ui.screens.worldscreen

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.unciv.ui.components.input.activate
import com.unciv.ui.components.input.gamepad.GamepadMenuHandler
import com.unciv.ui.components.input.gamepad.GamepadState
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.activePopup
import com.unciv.ui.screens.worldscreen.mainmenu.WorldScreenMenuPopup
import com.unciv.view.MapUnitView
import com.unciv.view.isAirUnit
import com.unciv.view.isCivilian

/**
 * Gamepad controls for the [WorldScreen].
 *
 * Map mode (no popup open):
 * - Left stick / D-pad: pan the map, the reticle in the screen center marks the "hovered" tile
 * - A: select the unit/city on the reticle tile, or move/attack there with the selected unit
 * - B: deselect
 * - L1 / R1: previous / next of the same kind as the selection (cities, land military, civilians,
 *   naval, air units), or the previous / next idle unit when nothing is selected
 * - L2 / R2: zoom out / in
 * - Start: game menu, Select: Empire overview
 * - Y: the "do next thing" button (next unit / pick tech / next turn ...)
 * - Other on-screen buttons stay touch-only for now
 *
 * With a popup open the normal menu navigation of [GamepadMenuHandler] applies.
 */
class WorldScreenGamepad(private val worldScreen: WorldScreen) : GamepadMenuHandler(worldScreen) {
    private val mapHolder get() = worldScreen.mapHolder

    private val reticleDot = ImageGetter.getCircle(Color.WHITE, 10f).apply {
        touchable = Touchable.disabled
        isVisible = false
    }
    private val reticleRing = ImageGetter.getCircle(Color(0f, 0f, 0f, 0.6f), 14f).apply {
        touchable = Touchable.disabled
        isVisible = false
    }

    override fun navigationRoot(): Group? = worldScreen.activePopup

    private fun isMapMode() = worldScreen.activePopup == null

    override fun keyDown(keyCode: Int): Boolean {
        val isDpad = GamepadState.isDpadKey(keyCode)
        if (!isDpad && !GamepadState.isGamepadKey(keyCode)) return false

        if (!isMapMode()) {
            val popup = worldScreen.activePopup
            if (keyCode == Input.Keys.BUTTON_START && popup is WorldScreenMenuPopup) {
                popup.close()
                return true
            }
            return super.keyDown(keyCode)
        }

        // Map mode
        when (keyCode) {
            Input.Keys.BUTTON_A -> mapHolder.gamepadReticleTile?.let { mapHolder.gamepadConfirm(it) }
            Input.Keys.BUTTON_B -> deselect()
            Input.Keys.BUTTON_Y -> worldScreen.nextTurnButton.activate() // disabled while waiting / not our turn
            Input.Keys.BUTTON_L1 -> cycleSelection(previous = true)
            Input.Keys.BUTTON_R1 -> cycleSelection(previous = false)
            Input.Keys.BUTTON_START -> WorldScreenMenuPopup(worldScreen)
            Input.Keys.BUTTON_SELECT -> worldScreen.openEmpireOverview()
            // D-pad: let the regular arrow-key map panning handle it
            Input.Keys.DPAD_UP, Input.Keys.DPAD_DOWN, Input.Keys.DPAD_LEFT, Input.Keys.DPAD_RIGHT -> return false
        }
        return true
    }

    // region Bumper cycling

    private enum class CycleCategory { Cities, LandMilitary, Civilians, Naval, Air }

    private fun categoryOf(unit: MapUnitView) = when {
        unit.isAirUnit() -> CycleCategory.Air
        unit.isCivilian() -> CycleCategory.Civilians
        unit.getBaseUnit().isWaterUnit -> CycleCategory.Naval
        else -> CycleCategory.LandMilitary
    }

    private fun nextIndex(current: Int, size: Int, previous: Boolean) =
        if (current < 0) 0 else (current + (if (previous) -1 else 1) + size) % size

    /** L1/R1: cycle through things of the same kind as the current selection, idle units when nothing is selected */
    private fun cycleSelection(previous: Boolean) {
        val unitTable = worldScreen.bottomUnitTable
        val selectedUnit = unitTable.selectedUnit
        when {
            unitTable.selectedCity != null -> cycleCities(previous)
            selectedUnit != null -> cycleUnits(categoryOf(selectedUnit), selectedUnit, previous)
            else -> unitTable.cycleIdleUnit(previous)
        }
    }

    private fun cycleUnits(category: CycleCategory, current: MapUnitView, previous: Boolean) {
        val units = worldScreen.selectedGameView.civView.getUnits().filter { categoryOf(it) == category }
        if (units.isEmpty()) return
        val next = units[nextIndex(units.indexOf(current), units.size, previous)]
        mapHolder.setCenterPosition(next.getTile().position(), selectUnit = false)
        worldScreen.bottomUnitTable.selectUnit(next)
        worldScreen.shouldUpdate = true
    }

    private fun cycleCities(previous: Boolean) {
        val unitTable = worldScreen.bottomUnitTable
        val cities = worldScreen.selectedGameView.civView.cities()
        if (cities.isEmpty()) return
        val currentTile = unitTable.selectedCity?.getCenterTile()
        val currentIndex = cities.indexOfFirst { it.getCenterTile().position() == currentTile?.position() }
        val next = cities[nextIndex(currentIndex, cities.size, previous)]
        val nextTile = next.getCenterTile()
        if (currentTile != null && currentTile.position() != nextTile.position())
            mapHolder.tileGroups[currentTile]?.layerCityButton?.moveUp()
        mapHolder.setCenterPosition(nextTile.position(), selectUnit = false)
        unitTable.citySelected(next)
        mapHolder.tileGroups[nextTile]?.layerCityButton?.moveDown()
        worldScreen.shouldUpdate = true
    }

    // endregion

    private fun deselect() {
        val unitTable = worldScreen.bottomUnitTable
        if (mapHolder.unitActionOverlays.isNotEmpty()) mapHolder.removeUnitActionOverlay()
        if (unitTable.selectedUnit != null || unitTable.selectedCity != null || unitTable.selectedSpy != null)
            injectBack() // WorldScreen's Escape handler deselects
    }

    override fun update(delta: Float) {
        super.update(delta)
        updateReticle()

        if (!isMapMode()) return

        // Analog panning, same speed as the arrow keys at full tilt
        val x = GamepadState.deadzone(GamepadState.leftX)
        val y = GamepadState.deadzone(GamepadState.leftY)
        if (x != 0f || y != 0f) {
            val ticks = delta * 100f // KeyboardPanningListener moves one step every 10ms
            mapHolder.doKeyOrMousePanning(-x * ticks, y * ticks)
        }

        // Trigger zoom: R2 in, L2 out
        val zoom = GamepadState.effectiveRightTrigger() - GamepadState.effectiveLeftTrigger()
        if (zoom > 0.1f || zoom < -0.1f)
            mapHolder.zoom(mapHolder.scaleX * (1f + zoom * 1.5f * delta))
    }

    private fun updateReticle() {
        val show = GamepadState.controllerMode
        reticleDot.isVisible = show
        reticleRing.isVisible = show
        if (show) {
            val stage = worldScreen.stage
            if (reticleDot.stage == null) {
                // Just above the map, below all HUD elements
                stage.addActor(reticleRing)
                stage.addActor(reticleDot)
                reticleRing.zIndex = mapHolder.zIndex + 1
                reticleDot.zIndex = mapHolder.zIndex + 2
            }
            reticleRing.setPosition((stage.width - reticleRing.width) / 2f, (stage.height - reticleRing.height) / 2f)
            reticleDot.setPosition((stage.width - reticleDot.width) / 2f, (stage.height - reticleDot.height) / 2f)
        }
        if (mapHolder.updateGamepadReticle(show))
            worldScreen.updateGamepadTileInfo()
    }
}
