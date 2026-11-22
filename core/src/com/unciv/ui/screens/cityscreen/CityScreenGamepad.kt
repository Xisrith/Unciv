package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.Group
import com.unciv.ui.components.input.gamepad.GamepadMenuHandler
import com.unciv.ui.components.input.gamepad.GamepadState
import com.unciv.ui.popups.activePopup

/**
 * Gamepad controls for the [CityScreen]:
 * - Two panels: production (left) and city stats / focus (right). L2 / R2 switch between them.
 * - D-pad + A navigate within the active panel, B leaves the city
 * - X: "more actions" - on a queue entry: move up / down, remove; elsewhere: rename, raze / annex
 * - L1 / R1: previous / next city
 */
class CityScreenGamepad(private val cityScreen: CityScreen) : GamepadMenuHandler(cityScreen) {
    private var rightPanel = false
    private var leftTriggerWasDown = false
    private var rightTriggerWasDown = false

    override fun navigationRoot(): Group? = cityScreen.activePopup
        ?: if (rightPanel) cityScreen.gamepadStatsPanel else cityScreen.gamepadProductionPanel

    override fun keyDown(keyCode: Int): Boolean {
        if (!GamepadState.isGamepadKey(keyCode) && !GamepadState.isDpadKey(keyCode)) return false
        if (cityScreen.activePopup == null) {
            when (keyCode) {
                Input.Keys.BUTTON_L2 -> { switchPanel(right = false); return true }
                Input.Keys.BUTTON_R2 -> { switchPanel(right = true); return true }
                Input.Keys.BUTTON_X -> { moreActions(); return true }
                Input.Keys.BUTTON_L1 -> { cityScreen.page(-1); return true }
                Input.Keys.BUTTON_R1 -> { cityScreen.page(1); return true }
            }
        }
        return super.keyDown(keyCode)
    }

    private fun switchPanel(right: Boolean) {
        if (rightPanel == right) return
        rightPanel = right
        navigator.clear() // auto-focus picks the top entry of the new panel
    }

    private fun moreActions() {
        val queueIndex = if (rightPanel) null else cityScreen.gamepadProductionPanel.queueIndexOf(navigator.focused)
        if (queueIndex != null) GamepadQueueEntryPopup(cityScreen, queueIndex)
        else GamepadCityActionsPopup(cityScreen)
    }

    override fun update(delta: Float) {
        // Analog triggers: switch on the press, not while held
        val left = GamepadState.leftTrigger > 0.6f
        val right = GamepadState.rightTrigger > 0.6f
        if (cityScreen.activePopup == null) {
            if (left && !leftTriggerWasDown) switchPanel(right = false)
            if (right && !rightTriggerWasDown) switchPanel(right = true)
        }
        leftTriggerWasDown = left
        rightTriggerWasDown = right
        super.update(delta)
    }
}
