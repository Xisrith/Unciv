package com.unciv.ui.components.input.gamepad

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

/** One entry of the [GamepadPromptBar]: which [button] does what ([label]) */
data class GamepadPrompt(val button: String, val label: String) {
    companion object {
        const val A = "A"
        const val B = "B"
        const val X = "X"
        const val Y = "Y"
        const val BUMPERS = "L1/R1"
        const val TRIGGERS = "L2/R2"
        const val START = "Start"
    }
}

/**
 * Bottom-center strip of contextual button prompts ("(A) Select  (B) Back ..."), shown on every
 * screen in controller mode. Screens keep the bottom [HEIGHT] free for it.
 */
class GamepadPromptBar : Table(BaseScreen.skin) {
    companion object {
        /** Vertical space screens should reserve at the bottom in controller mode */
        const val HEIGHT = 36f
    }

    private var shown: List<GamepadPrompt>? = null

    init {
        touchable = Touchable.disabled
        background = ImageGetter.getWhiteDotDrawable().tint(Color(0f, 0f, 0f, 0.55f))
    }

    fun show(prompts: List<GamepadPrompt>, stage: Stage) {
        if (prompts != shown) {
            shown = prompts
            clear()
            pad(4f, 12f, 4f, 2f)
            for (prompt in prompts) {
                add(glyph(prompt.button)).padRight(5f)
                add(prompt.label.toLabel(Color.WHITE, 15)).padRight(16f)
            }
            pack()
        }
        isVisible = prompts.isNotEmpty()
        setPosition((stage.width - width) / 2f, 3f)
    }

    private fun glyph(button: String): Actor {
        val color = when (button) {
            GamepadPrompt.A -> Color(0.36f, 0.73f, 0.31f, 1f)
            GamepadPrompt.B -> Color(0.86f, 0.25f, 0.22f, 1f)
            GamepadPrompt.X -> Color(0.24f, 0.49f, 0.86f, 1f)
            GamepadPrompt.Y -> Color(0.95f, 0.78f, 0.2f, 1f)
            else -> null
        }
        return if (color != null)
            button.toLabel(Color.WHITE, 14).surroundWithCircle(24f, resizeActor = false, color = color)
        else // shoulder buttons, Start/Select: plain gold text
            button.toLabel(Color.GOLD, 14)
    }
}
