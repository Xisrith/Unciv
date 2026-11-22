package com.unciv.ui.components.input.gamepad

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.Disableable
import com.unciv.ui.components.input.ActorAttachments
import com.unciv.ui.components.input.activate
import com.unciv.ui.components.widgets.ZoomableScrollPane
import com.unciv.ui.images.ImageGetter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Spatial "focus" navigation for gamepads over any scene2d UI.
 *
 * - Collects interactive widgets (buttons, select boxes, sliders, text fields and anything with an
 *   Unciv activation handler) below a root group
 * - Moves focus to the nearest widget in a D-pad direction
 * - Draws a highlight frame around the focused widget
 * - "Activates" the focused widget by synthesizing a tap at its center, so every kind of widget
 *   behaves exactly as if it had been touched
 *
 * @param isExcluded Subtrees for which this returns `true` are never searched (e.g. the world map with its thousands of tiles)
 */
class GamepadNavigator(
    private val stage: Stage,
    private val isExcluded: (Actor) -> Boolean = { false }
) {
    var focused: Actor? = null
        private set

    /** Stage position of the last focused widget, used to re-acquire a nearby one when the UI is rebuilt */
    private var lastFocusCenter: Vector2? = null
    private val highlight = FocusHighlight()

    // region Candidate collection

    private fun isFocusable(actor: Actor): Boolean {
        if (actor.touchable == Touchable.disabled) return false
        if (actor is Disableable && actor.isDisabled) return false
        if (actor.width < 4f || actor.height < 4f) return false
        // Full-screen click catchers (e.g. a Popup's "click behind to close" area) are not menu entries
        if (actor.width >= stage.width * 0.9f && actor.height >= stage.height * 0.9f) return false
        return when (actor) {
            is Button, is SelectBox<*>, is Slider, is TextField -> true
            else -> (actor.userObject as? ActorAttachments)?.activationActions != null
        }
    }

    /** Leaf-like widgets whose children should not be offered separately */
    private fun isAtomic(actor: Actor) = actor is Button || actor is SelectBox<*> || actor is Slider || actor is TextField

    fun collect(root: Group): List<Actor> {
        val result = ArrayList<Actor>()
        fun visit(actor: Actor) {
            if (!actor.isVisible || isExcluded(actor)) return
            if (actor.touchable == Touchable.disabled) return
            if (actor !== root && isFocusable(actor) && isOnScreen(actor)) result += actor
            if (actor is Group && !isAtomic(actor))
                for (child in actor.children) visit(child)
        }
        visit(root)
        return result
    }

    private fun isOnScreen(actor: Actor): Boolean {
        // Widgets inside a scroll pane may be scrolled out of view - we scroll them in when focused
        if (actor.firstAscendant(ScrollPane::class.java) != null) return true
        val c = center(actor)
        return c.x >= 0f && c.y >= 0f && c.x <= stage.width && c.y <= stage.height
    }

    private fun center(actor: Actor): Vector2 =
        actor.localToStageCoordinates(Vector2(actor.width / 2f, actor.height / 2f))

    private fun isStillValid(actor: Actor?, root: Group): Boolean =
        actor != null && actor.stage === stage && actor.isDescendantOf(root) && actor.ascendantsVisible()

    // endregion

    // region Focus handling

    fun setFocus(actor: Actor?) {
        focused = actor
        highlight.target = actor
        if (actor == null) {
            highlight.remove()
            return
        }
        lastFocusCenter = center(actor)
        if (highlight.stage == null) stage.addActor(highlight)
        highlight.toFront()
        scrollIntoView(actor)
    }

    fun clear() {
        setFocus(null)
        lastFocusCenter = null
    }

    /** Make sure something sensible is focused within [root] (keeps the current focus if still valid) */
    fun ensureFocus(root: Group) {
        if (isStillValid(focused, root)) return
        val candidates = collect(root)
        if (candidates.isEmpty()) {
            if (focused != null) setFocus(null)
            return
        }
        val anchor = lastFocusCenter
        val pick = if (anchor != null)
            candidates.minBy { center(it).dst2(anchor) }
        else // top-most, then left-most
            candidates.minWith(compareBy<Actor> { -center(it).y.toInt() / 10 }.thenBy { center(it).x })
        setFocus(pick)
    }

    /** Move focus in a direction. [dirX]: -1 left / +1 right, [dirY]: +1 up / -1 down (stage coordinates) */
    fun move(root: Group, dirX: Int, dirY: Int): Boolean {
        val current = focused
        if (!isStillValid(current, root)) {
            ensureFocus(root)
            return focused != null
        }
        val from = center(current!!)
        var best: Actor? = null
        var bestScore = Float.MAX_VALUE
        for (candidate in collect(root)) {
            if (candidate === current) continue
            val to = center(candidate)
            val dx = to.x - from.x
            val dy = to.y - from.y
            val primary = if (dirX != 0) dx * dirX else dy * dirY
            if (primary <= 2f) continue // not in that direction
            val secondary = abs(if (dirX != 0) dy else dx)
            val score = primary + secondary * 2.5f
            if (score < bestScore) {
                bestScore = score
                best = candidate
            }
        }
        if (best != null) setFocus(best)
        return true
    }

    /** Left/right on sliders and select boxes changes their value. @return `true` if handled */
    fun adjust(dir: Int): Boolean {
        when (val actor = focused) {
            is Slider -> {
                actor.value = actor.value + actor.stepSize * dir
                return true
            }
            is SelectBox<*> -> {
                val size = actor.items.size
                if (size == 0) return true
                actor.selectedIndex = (actor.selectedIndex + dir + size) % size
                return true
            }
            else -> return false
        }
    }

    /** Activate the focused widget. @return `true` if something was focused */
    fun activate(): Boolean {
        val actor = focused ?: return false
        when (actor) {
            is SelectBox<*> -> adjust(1) // cycling is far easier than a drop-down list with a gamepad
            is TextField -> {
                stage.keyboardFocus = actor
                tap(actor) // a tap brings up the on-screen keyboard as usual
            }
            else -> tap(actor)
        }
        return true
    }

    /** Synthesize a complete tap at the center of [actor], exactly as a finger would */
    private fun tap(actor: Actor) {
        val pos = stage.stageToScreenCoordinates(center(actor))
        stage.touchDown(pos.x.toInt(), pos.y.toInt(), 0, Input.Buttons.LEFT)
        stage.touchUp(pos.x.toInt(), pos.y.toInt(), 0, Input.Buttons.LEFT)
    }

    private fun scrollIntoView(actor: Actor) {
        var child: Actor = actor
        var pane = actor.firstAscendant(ScrollPane::class.java)
        while (pane != null) {
            val content = pane.actor ?: return
            if (pane !is ZoomableScrollPane) {
                val pos = child.localToActorCoordinates(content, Vector2(0f, 0f))
                pane.scrollTo(pos.x, pos.y, child.width, child.height)
            }
            child = pane
            pane = pane.parent?.firstAscendant(ScrollPane::class.java)
        }
    }

    // endregion

    /** Per-frame: keep the highlight on top and scroll with the analog stick */
    fun update(root: Group?, delta: Float) {
        if (root == null) return
        if (focused != null && !isStillValid(focused, root)) setFocus(null)
        if (highlight.stage != null) highlight.toFront()

        val sx = GamepadState.deadzone(GamepadState.leftX)
        val sy = GamepadState.deadzone(GamepadState.leftY)
        if (sx == 0f && sy == 0f) return
        val pane = focused?.firstAscendant(ScrollPane::class.java)?.takeUnless { it is ZoomableScrollPane }
            ?: findLargestScrollPane(root)
            ?: return
        val speed = 1200f * delta
        pane.scrollX = pane.scrollX + sx * speed
        pane.scrollY = pane.scrollY + sy * speed // stick down (positive) scrolls content down
        pane.updateVisualScroll()
    }

    private fun findLargestScrollPane(root: Group): ScrollPane? {
        var best: ScrollPane? = null
        fun visit(actor: Actor) {
            if (!actor.isVisible || isExcluded(actor)) return
            if (actor is ScrollPane && actor !is ZoomableScrollPane && (actor.isScrollY || actor.isScrollX)) {
                if (best == null || actor.width * actor.height > best!!.width * best!!.height) best = actor
            }
            if (actor is Group) for (c in actor.children) visit(c)
        }
        visit(root)
        return best
    }

    /** Draws a pulsing frame around the focused widget, in stage coordinates */
    private class FocusHighlight : Actor() {
        var target: Actor? = null
        private val dot = ImageGetter.getWhiteDotDrawable()
        private val frameColor = Color(1f, 0.82f, 0.25f, 1f)
        private var time = 0f
        private val tmp = Color()

        init {
            touchable = Touchable.disabled
        }

        override fun act(delta: Float) {
            super.act(delta)
            time += delta
        }

        override fun draw(batch: Batch, parentAlpha: Float) {
            val t = target ?: return
            if (t.stage == null) return
            val a = t.localToStageCoordinates(Vector2(0f, 0f))
            val b = t.localToStageCoordinates(Vector2(t.width, t.height))
            val pad = 3f
            val thickness = 3f
            val x = min(a.x, b.x) - pad
            val y = min(a.y, b.y) - pad
            val w = abs(b.x - a.x) + pad * 2
            val h = abs(b.y - a.y) + pad * 2
            tmp.set(batch.color)
            batch.setColor(frameColor.r, frameColor.g, frameColor.b, max(0.55f, 0.8f + 0.2f * sin(time * 5f)) * parentAlpha)
            dot.draw(batch, x, y, w, thickness)                  // bottom
            dot.draw(batch, x, y + h - thickness, w, thickness)  // top
            dot.draw(batch, x, y, thickness, h)                  // left
            dot.draw(batch, x + w - thickness, y, thickness, h)  // right
            batch.color = tmp
        }
    }
}

/** Activates the first widget with an Unciv activation handler found in this actor's subtree */
internal fun Actor.activateFirstActivatable(): Boolean {
    if (isVisible && (userObject as? ActorAttachments)?.activationActions != null && activate()) return true
    if (this is Group) for (child in children) if (child.activateFirstActivatable()) return true
    return false
}
