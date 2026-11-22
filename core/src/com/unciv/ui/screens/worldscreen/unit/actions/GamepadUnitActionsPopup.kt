package com.unciv.ui.screens.worldscreen.unit.actions

import com.badlogic.gdx.graphics.Color
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.view.MapUnitView

/**
 * Gamepad "X": every action of the selected unit in one list, so no paging through the
 * on-screen action column is needed. Actions run through the same code as the regular buttons
 * (including confirmation dialogs and auto unit cycling).
 */
class GamepadUnitActionsPopup(
    private val worldScreen: WorldScreen,
    private val unit: MapUnitView
) : Popup(worldScreen, Scrollability.WithoutButtons) {

    init {
        addGoodSizedLabel(unit.displayName(), 24).padBottom(10f).row()

        val actions = UiUnitActions.getUnitActions(unit).sortedByDescending { it.useFrequency }
        for (unitAction in actions) {
            val fontColor = if (unitAction.isCurrentAction) Color.YELLOW else Color.WHITE
            val button = IconTextButton(unitAction.title, unitAction.getIcon(), fontColor = fontColor)
            if (unitAction.action == null) {
                button.disable()
            } else {
                button.onActivation(unitAction.uncivSound) {
                    close()
                    worldScreen.unitActionsTable.activateAction(unitAction, unit)
                }
            }
            add(button).growX().padBottom(4f).row()
        }

        addCloseButton()
        open()
    }
}
