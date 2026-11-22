package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.models.ruleset.PerpetualConstruction
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.AutoScrollPane
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 * Controller layout for the left side of the city screen: just the production queue and an
 * "Add to queue" button (which opens [GamepadConstructionPickerPopup]).
 *
 *     Production
 *     [icon] Thing that's being made
 *            4 turns
 *     [icon] Thing that's next
 *            7 turns
 *     [ Add to queue ]
 *
 * Gamepad X on a queue entry opens [GamepadQueueEntryPopup] (move up / down, remove).
 */
class GamepadProductionPanel(private val cityScreen: CityScreen) : Table(BaseScreen.skin) {
    private val entriesTable = Table()
    private val scrollPane = AutoScrollPane(entriesTable)
    private val entryIndex = HashMap<Actor, Int>()
    private val addButton = "Add to queue".toTextButton()

    init {
        background = BaseScreen.skinStrings.getUiBackground(
            "CityScreen/CityConstructionTable/ConstructionsQueueTable",
            tintColor = ImageGetter.CHARCOAL
        )
        pad(10f)
        defaults().left()
        add("Production".toLabel(fontSize = 24)).padBottom(8f).row()
        add(scrollPane).growX().maxHeight(cityScreen.stage.height * 0.7f).row()
        add(addButton).growX().padTop(10f)
        addButton.onActivation { cityScreen.openGamepadConstructionPicker() }
    }

    /** The queue index of the entry [actor] belongs to, or `null` if it isn't part of a queue entry */
    fun queueIndexOf(actor: Actor?): Int? {
        var current = actor
        while (current != null && current !== this) {
            entryIndex[current]?.let { return it }
            current = current.parent
        }
        return null
    }

    fun update() {
        val constructions = cityScreen.cityView.constructions
        val queue = constructions.constructionQueue
        entriesTable.clear()
        entryIndex.clear()
        entriesTable.defaults().left().growX().padBottom(6f)

        if (queue.isEmpty())
            entriesTable.add("Pick a construction".toLabel(Color.LIGHT_GRAY)).row()

        queue.forEachIndexed { index, name ->
            val construction = constructions.getConstruction(name)
            val turns = if (name in PerpetualConstruction.perpetualConstructionsMap) Fonts.infinity.toString()
                else constructions.getTurnsToConstructionString(
                    construction, constructions.isFirstConstructionOfItsKind(queue, index, name)
                ).trim()

            val row = Table()
            row.touchable = Touchable.enabled
            row.add(ImageGetter.getConstructionPortrait(name, 40f)).padRight(10f)
            val texts = Table()
            texts.add(name.toLabel(fontSize = if (index == 0) 20 else 16)).left().row()
            texts.add(turns.toLabel(Color.LIGHT_GRAY, 14)).left()
            row.add(texts).growX().left()
            row.onClick {
                cityScreen.selectedQueueEntry = index
                cityScreen.selectConstructionFromQueue(index)
                cityScreen.updateAsync()
            }
            entriesTable.add(row).row()
            entryIndex[row] = index
        }

        addButton.isEnabled = cityScreen.canCityBeChanged()
        pack()
        setPosition(CityScreen.posFromEdge, cityScreen.stage.height - CityScreen.posFromEdge, Align.topLeft)
    }
}
