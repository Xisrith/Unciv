package com.unciv.ui.screens.worldscreen.bottombar

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.gamepad.GamepadState
import com.unciv.ui.components.widgets.BasicTileInfo
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.DebugUtils
import com.unciv.view.CivView
import com.unciv.view.TileView

class TileInfoTable(
    private val worldScreen: WorldScreen,
    override var civView: CivView = worldScreen.selectedGameView.civView
) : BasicTileInfo(
    worldScreen,
    civView,
    null,
    false,
    "WorldScreen/TileInfoTable", 1f, "WorldScreen/TileInfoTableBorder"
) {
    // For pick-up by UiElementDocsWriter
    // skinStrings.getUiBackground("WorldScreen/TileInfoTable")
    // skinStrings.getUiBackground("WorldScreen/TileInfoTableBorder")

    override fun shouldBeVisible(tileView: TileView) = DebugUtils.VISIBLE_MAP || civView.hasExplored(tileView)

    /** Controller layout: units on the hovered tile get their own card, see [GamepadUnitHoverCard] */
    override val hideUnits get() = GamepadState.controllerMode
    /** Controller layout: "Farm - 2 turns" is the worker's business - shown on its hover card; the tile shows the farm once built */
    override val hideWorkInProgress get() = GamepadState.controllerMode

    override fun Table.addAdditionalContent(tileView: TileView) {
        if (DebugUtils.VISIBLE_MAP)
            add(tileView.position().toPrettyString().toLabel()).colspan(2).pad(5f)
        if (DebugUtils.SHOW_TILE_IMAGE_LOCATIONS){
            val imagesString = "Images: " + worldScreen.mapHolder.tileGroups[tileView]!!.layerTerrain.tileBaseImages.joinToString{"\n"+it.name}
            add(imagesString.toLabel())
        }
    }

    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)
}
