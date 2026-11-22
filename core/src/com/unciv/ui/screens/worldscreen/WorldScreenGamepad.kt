package com.unciv.ui.screens.worldscreen

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.unciv.ui.components.input.gamepad.GamepadMenuHandler
import com.unciv.ui.components.input.gamepad.GamepadState
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.activePopup
import com.unciv.ui.screens.worldscreen.mainmenu.WorldScreenMenuPopup

/**
 * Gamepad controls for the [WorldScreen].
 *
 * Map mode (no popup open):
 * - Left stick / D-pad: pan the map, the reticle in the screen center marks the "hovered" tile
 * - A: select the unit/city on the reticle tile, or move/attack there with the selected unit
 * - B: deselect
 * - L1 / R1: previous / next idle unit
 * - L2 / R2: zoom out / in
 * - Start: game menu, Select: Empire overview
 * - Y: "HUD mode" - move a highlight over the on-screen buttons (unit actions, next turn, tech, ...), B or Y to leave
 *
 * With a popup open (or in HUD mode) the normal menu navigation of [GamepadMenuHandler] applies.
 */
class WorldScreenGamepad(private val worldScreen: WorldScreen) : GamepadMenuHandler(worldScreen) {
    private val mapHolder get() = worldScreen.mapHolder

    /** Navigating the on-screen buttons instead of the map */
    private var hudMode = false

    private val reticleDot = ImageGetter.getCircle(Color.WHITE, 10f).apply {
        touchable = Touchable.disabled
        isVisible = false
    }
    private val reticleRing = ImageGetter.getCircle(Color(0f, 0f, 0f, 0.6f), 14f).apply {
        touchable = Touchable.disabled
        isVisible = false
    }

    override fun navigationRoot(): Group? = worldScreen.activePopup ?: if (hudMode) worldScreen.stage.root else null

    private fun isMapMode() = !hudMode && worldScreen.activePopup == null

    override fun keyDown(keyCode: Int): Boolean {
        val isDpad = GamepadState.isDpadKey(keyCode)
        if (!isDpad && !GamepadState.isGamepadKey(keyCode)) return false

        if (!isMapMode()) {
            val popup = worldScreen.activePopup
            if (popup == null) { // HUD mode
                if (keyCode == Input.Keys.BUTTON_B || keyCode == Input.Keys.BUTTON_Y) {
                    leaveHudMode()
                    return true
                }
                if (keyCode == Input.Keys.BUTTON_A) {
                    // Most HUD buttons change the selection or open a screen - go back to the map afterwards
                    super.keyDown(keyCode)
                    leaveHudMode()
                    return true
                }
            } else if (keyCode == Input.Keys.BUTTON_START && popup is WorldScreenMenuPopup) {
                popup.close()
                return true
            }
            return super.keyDown(keyCode)
        }

        // Map mode
        when (keyCode) {
            Input.Keys.BUTTON_A -> mapHolder.gamepadReticleTile?.let { mapHolder.gamepadConfirm(it) }
            Input.Keys.BUTTON_B -> deselect()
            Input.Keys.BUTTON_Y -> {
                hudMode = true
                navigator.clear()
                navigator.ensureFocus(worldScreen.stage.root)
            }
            Input.Keys.BUTTON_L1 -> worldScreen.bottomUnitTable.cycleIdleUnit(previous = true)
            Input.Keys.BUTTON_R1 -> worldScreen.bottomUnitTable.cycleIdleUnit(previous = false)
            Input.Keys.BUTTON_START -> WorldScreenMenuPopup(worldScreen)
            Input.Keys.BUTTON_SELECT -> worldScreen.openEmpireOverview()
            // D-pad: let the regular arrow-key map panning handle it
            Input.Keys.DPAD_UP, Input.Keys.DPAD_DOWN, Input.Keys.DPAD_LEFT, Input.Keys.DPAD_RIGHT -> return false
        }
        return true
    }

    private fun deselect() {
        val unitTable = worldScreen.bottomUnitTable
        if (mapHolder.unitActionOverlays.isNotEmpty()) mapHolder.removeUnitActionOverlay()
        if (unitTable.selectedUnit != null || unitTable.selectedCity != null || unitTable.selectedSpy != null)
            injectBack() // WorldScreen's Escape handler deselects
    }

    private fun leaveHudMode() {
        hudMode = false
        navigator.clear()
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
