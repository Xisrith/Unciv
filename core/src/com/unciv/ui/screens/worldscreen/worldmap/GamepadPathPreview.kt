package com.unciv.ui.screens.worldscreen.worldmap

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread
import com.unciv.view.MapUnitView
import com.unciv.view.TileView
import com.unciv.view.isAirUnit
import kotlinx.coroutines.Job

/**
 * Gamepad path preview: while a unit is selected, shows the route to the tile under the reticle -
 * small dots along this turn's steps and numbered markers where each turn ends.
 *
 * Pathfinding runs on a worker thread and only once the reticle has rested on a tile briefly,
 * so sweeping the stick across the map stays smooth.
 */
internal class GamepadPathPreview(private val mapHolder: WorldMapHolder) {
    private val markers = ArrayList<Actor>()
    private var generation = 0
    private var job: Job? = null

    private var shownKey: Triple<MapUnitView?, TileView?, TileView?>? = null
    private var pendingKey: Triple<MapUnitView?, TileView?, TileView?>? = null
    private var pendingTime = 0f

    private val stepColor = Color(0.53f, 0.81f, 0.98f, 0.9f)
    private val turnColor = Color(0.08f, 0.32f, 0.55f, 0.95f)

    /** Forget the current preview so it is recomputed (e.g. after the unit moved) */
    fun invalidate() {
        shownKey = null
        pendingKey = null
        clear()
    }

    fun update(unit: MapUnitView?, target: TileView?, delta: Float) {
        val key = Triple(unit, unit?.getTile(), target)
        if (key == shownKey) return
        if (key != pendingKey) {
            pendingKey = key
            pendingTime = 0f
            clear()
            return
        }
        pendingTime += delta
        if (pendingTime < 0.12f) return
        shownKey = key
        compute(unit, target)
    }

    private fun compute(unit: MapUnitView?, target: TileView?) {
        val gen = ++generation
        job?.cancel()
        clear()
        if (unit == null || target == null || unit.getTile() == target || unit.isAirUnit()) return

        job = Concurrency.run("GamepadPathPreview") {
            val (steps, turnEnds) = try {
                if (unit.canReachInCurrentTurn(target)) {
                    unit.getPathToTile(target) to listOf(target)
                } else {
                    val turnEnds = unit.getShortestPath(target)
                    val firstTurn = if (turnEnds.isEmpty()) emptyList() else unit.getPathToTile(turnEnds.first())
                    firstTurn to turnEnds
                }
            } catch (_: Exception) {
                // Game state changed under us (unit moved, died...) - simply show nothing
                emptyList<TileView>() to emptyList<TileView>()
            }
            launchOnGLThread {
                if (gen == generation) show(steps, turnEnds)
            }
        }
    }

    private fun show(steps: List<TileView>, turnEnds: List<TileView>) {
        clear()
        for (tile in steps)
            if (tile !in turnEnds) addMarker(tile, ImageGetter.getCircle(stepColor, 10f))
        turnEnds.forEachIndexed { index, tile ->
            val label = (index + 1).toString().toLabel(Color.WHITE, 14)
            addMarker(tile, label.surroundWithCircle(26f, resizeActor = false, color = turnColor))
        }
    }

    private fun addMarker(tile: TileView, actor: Actor) {
        val group = mapHolder.tileGroups[tile] ?: return
        actor.touchable = Touchable.disabled
        actor.setPosition(group.x + (group.width - actor.width) / 2f, group.y + (group.height - actor.height) / 2f)
        mapHolder.addActorToTileGroupMap(actor)
        actor.toFront()
        markers += actor
    }

    fun clear() {
        for (marker in markers) marker.remove()
        markers.clear()
    }
}
