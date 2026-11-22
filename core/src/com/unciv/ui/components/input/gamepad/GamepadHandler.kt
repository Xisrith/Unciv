package com.unciv.ui.components.input.gamepad

import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.ui.components.tilegroups.TileGroupMap
import com.unciv.ui.popups.activePopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.UncivStage

/** Receives gamepad input for one screen. Wired up through [UncivStage], one instance per [BaseScreen]. */
interface GamepadHandler {
    /** @return `true` if the key was consumed and must not reach the normal keyboard handling */
    fun keyDown(keyCode: Int): Boolean
    fun keyUp(keyCode: Int): Boolean
    /** Called once per frame from the stage's act() */
    fun update(delta: Float)
}

/**
 * Default handler for every screen: menu navigation.
 * - D-pad: move the highlighted selection (left/right also change sliders and drop-downs)
 * - A: activate the highlighted widget
 * - B: back (same as Escape / Android back)
 * - Left stick: scroll
 */
open class GamepadMenuHandler(protected val screen: BaseScreen) : GamepadHandler {
    protected val navigator = GamepadNavigator(screen.stage) { it is TileGroupMap<*> }

    /** The group menu navigation works in, or `null` when menu navigation is inactive */
    protected open fun navigationRoot(): Group? = screen.activePopup ?: screen.stage.root

    protected fun injectBack() {
        val stage = screen.stage as? UncivStage ?: return
        stage.injectKey(Input.Keys.ESCAPE)
    }

    override fun keyDown(keyCode: Int): Boolean {
        val isDpad = GamepadState.isDpadKey(keyCode)
        if (!isDpad && !GamepadState.isGamepadKey(keyCode)) return false
        // Let a focused text field keep its cursor keys
        if (isDpad && screen.stage.keyboardFocus is TextField) return false
        val root = navigationRoot() ?: return false
        return when (keyCode) {
            Input.Keys.DPAD_UP -> { navigator.move(root, 0, 1); true }
            Input.Keys.DPAD_DOWN -> { navigator.move(root, 0, -1); true }
            Input.Keys.DPAD_LEFT -> { navigator.adjust(-1) || navigator.move(root, -1, 0) || onNothingInDirection(-1); true }
            Input.Keys.DPAD_RIGHT -> { navigator.adjust(1) || navigator.move(root, 1, 0) || onNothingInDirection(1); true }
            Input.Keys.BUTTON_A -> {
                navigator.ensureFocus(root)
                navigator.activate()
                true
            }
            Input.Keys.BUTTON_B -> {
                if (screen.stage.keyboardFocus is TextField) screen.stage.keyboardFocus = null
                injectBack()
                true
            }
            else -> true // swallow other gamepad buttons so they can't trigger Android fallbacks
        }
    }

    /** Hook for left/right presses with nothing further in that direction ([dirX] -1 / +1). @return `true` if handled */
    protected open fun onNothingInDirection(dirX: Int): Boolean = false

    override fun keyUp(keyCode: Int): Boolean = GamepadState.isGamepadKey(keyCode)

    /** Throttles the auto-focus search on screens that currently have nothing focusable */
    private var autoFocusCooldown = 0f

    override fun update(delta: Float) {
        val root = navigationRoot()
        if (root == null) {
            if (navigator.focused != null) navigator.clear()
            return
        }
        autoFocusCooldown -= delta
        if (GamepadState.controllerMode && autoFocusCooldown <= 0f) {
            navigator.ensureFocus(root)
            if (navigator.focused == null) autoFocusCooldown = 0.25f
        }
        navigator.update(root, delta)
    }
}
