package com.unciv.ui.components.input.gamepad

import com.badlogic.gdx.Input
import kotlin.math.abs

/**
 * Shared, platform-agnostic state of an attached gamepad (e.g. the built-in controls of a Retroid Pocket Nova).
 *
 * Buttons arrive as regular libGDX key events (`Input.Keys.BUTTON_*`), the D-pad as arrow keys.
 * Analog axes are not delivered by libGDX core, so the Android launcher writes them here
 * (from the UI thread) and the game reads them on the GL thread every frame.
 */
object GamepadState {
    /** True once a gamepad has been detected - enables the controller-specific HUD layout */
    @Volatile var controllerMode = false

    /** Left stick, -1..1, x positive = right, y positive = **down** (Android convention) */
    @Volatile var leftX = 0f
    @Volatile var leftY = 0f
    /** Right stick, same conventions as the left one */
    @Volatile var rightX = 0f
    @Volatile var rightY = 0f
    /** Analog triggers, 0..1 */
    @Volatile var leftTrigger = 0f
    @Volatile var rightTrigger = 0f

    /** Digital trigger buttons (some devices send L2/R2 as keys instead of / in addition to axes) */
    internal var leftTriggerKeyDown = false
    internal var rightTriggerKeyDown = false

    const val STICK_DEADZONE = 0.2f

    fun effectiveLeftTrigger() = if (leftTriggerKeyDown) 1f else leftTrigger
    fun effectiveRightTrigger() = if (rightTriggerKeyDown) 1f else rightTrigger

    /** Applies a radial-ish deadzone and rescales so the output starts at 0 just outside it */
    fun deadzone(value: Float): Float {
        val a = abs(value)
        if (a < STICK_DEADZONE) return 0f
        val scaled = (a - STICK_DEADZONE) / (1f - STICK_DEADZONE)
        return if (value < 0) -scaled else scaled
    }

    /** `true` while any analog input needs per-frame processing (used to keep rendering going) */
    fun isAnalogActive() =
        deadzone(leftX) != 0f || deadzone(leftY) != 0f ||
        deadzone(rightX) != 0f || deadzone(rightY) != 0f ||
        effectiveLeftTrigger() > 0.1f || effectiveRightTrigger() > 0.1f

    fun isGamepadKey(keyCode: Int) = keyCode in gamepadButtonCodes

    private val gamepadButtonCodes = setOf(
        Input.Keys.BUTTON_A, Input.Keys.BUTTON_B, Input.Keys.BUTTON_C,
        Input.Keys.BUTTON_X, Input.Keys.BUTTON_Y, Input.Keys.BUTTON_Z,
        Input.Keys.BUTTON_L1, Input.Keys.BUTTON_R1, Input.Keys.BUTTON_L2, Input.Keys.BUTTON_R2,
        Input.Keys.BUTTON_THUMBL, Input.Keys.BUTTON_THUMBR,
        Input.Keys.BUTTON_START, Input.Keys.BUTTON_SELECT, Input.Keys.BUTTON_MODE,
    )

    fun isDpadKey(keyCode: Int) = keyCode == Input.Keys.DPAD_UP || keyCode == Input.Keys.DPAD_DOWN ||
        keyCode == Input.Keys.DPAD_LEFT || keyCode == Input.Keys.DPAD_RIGHT
}
