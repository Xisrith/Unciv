package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.models.stats.Stat
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.pickerscreens.CityRenamePopup

/** A vertical list of full-width buttons - easy to walk with the D-pad */
private fun Popup.addMenuButton(text: String, enabled: Boolean, action: () -> Unit) {
    val button = text.toTextButton()
    if (enabled) button.onActivation {
        close()
        action()
    } else button.disable()
    add(button).growX().padBottom(4f).row()
}

/** "Add to queue" from the gamepad production panel: everything that can be queued right now, by category */
internal class GamepadConstructionPickerPopup(
    private val cityScreen: CityScreen,
    dtos: List<ConstructionButtonDTO>,
    private val constructionsTable: CityConstructionsTable
) : Popup(cityScreen, Scrollability.WithoutButtons) {

    init {
        addGoodSizedLabel("Add to queue", 24).padBottom(10f).row()

        val categories = linkedMapOf<String, MutableList<ConstructionButtonDTO>>(
            "Units" to mutableListOf(), "Buildings" to mutableListOf(), "Wonders" to mutableListOf(),
            "National Wonders" to mutableListOf(), "Other" to mutableListOf()
        )
        for (dto in dtos) {
            if (dto.rejectionReason != null || constructionsTable.cannotAddConstructionToQueue(dto.construction)) continue
            val construction = dto.construction
            val category = when {
                construction is BaseUnit -> "Units"
                construction is Building && construction.isWonder -> "Wonders"
                construction is Building && construction.isNationalWonder -> "National Wonders"
                construction is Building -> "Buildings"
                else -> "Other"
            }
            categories[category]!! += dto
        }

        if (categories.values.all { it.isEmpty() })
            addGoodSizedLabel("Nothing can be added to the queue right now").row()

        for ((title, entries) in categories) {
            if (entries.isEmpty()) continue
            add(title.toLabel(Color.GOLD, 18)).left().padTop(8f).padBottom(4f).row()
            for (dto in entries) add(getEntry(dto)).growX().padBottom(4f).row()
        }

        addCloseButton()
        open()
    }

    private fun getEntry(dto: ConstructionButtonDTO): Table {
        val row = Table()
        row.touchable = Touchable.enabled
        row.add(ImageGetter.getConstructionPortrait(dto.construction.name, 40f)).padRight(10f)
        val texts = Table()
        texts.add(dto.construction.name.toLabel(fontSize = 18)).left().row()
        texts.add(dto.buttonText.toLabel(Color.LIGHT_GRAY, 14)).left()
        row.add(texts).growX().left()
        row.onClick {
            close()
            constructionsTable.addConstructionToQueue(dto.construction)
            cityScreen.updateAsync()
        }
        return row
    }
}

/** "Buy" from the gamepad production panel: everything purchasable in this city, with its price per currency */
internal class GamepadBuyPopup(
    private val cityScreen: CityScreen,
    dtos: List<ConstructionButtonDTO>,
    private val buyButtonFactory: BuyButtonFactory
) : Popup(cityScreen, Scrollability.WithoutButtons) {

    init {
        addGoodSizedLabel("Buy", 24).padBottom(10f).row()
        val cityView = cityScreen.cityView

        val categories = linkedMapOf<String, MutableList<Table>>(
            "Units" to mutableListOf(), "Buildings" to mutableListOf(), "Wonders" to mutableListOf(),
            "National Wonders" to mutableListOf()
        )
        for (dto in dtos) {
            val construction = dto.construction as? INonPerpetualConstruction ?: continue
            for (stat in Stat.statsUsableToBuy) {
                if (!cityView.canBePurchasedWithStat(construction, stat)) continue
                val cost = cityView.constructions.getStatBuyCost(construction, stat) ?: continue
                val allowed = cityScreen.canChangeState &&
                    cityView.constructions.isConstructionPurchaseAllowed(construction, stat, cost)
                val category = when {
                    construction is BaseUnit -> "Units"
                    construction is Building && construction.isWonder -> "Wonders"
                    construction is Building && construction.isNationalWonder -> "National Wonders"
                    else -> "Buildings"
                }
                categories[category]!! += getEntry(construction, stat, cost, allowed)
            }
        }

        if (categories.values.all { it.isEmpty() })
            addGoodSizedLabel("Nothing can be bought in this city right now").row()

        for ((title, entries) in categories) {
            if (entries.isEmpty()) continue
            add(title.toLabel(Color.GOLD, 18)).left().padTop(8f).padBottom(4f).row()
            for (entry in entries) add(entry).growX().padBottom(4f).row()
        }

        addCloseButton()
        open()
    }

    private fun getEntry(construction: INonPerpetualConstruction, stat: Stat, cost: Int, allowed: Boolean): Table {
        val row = Table()
        row.add(ImageGetter.getConstructionPortrait(construction.name, 40f)).padRight(10f)
        row.add(construction.name.toLabel(if (allowed) Color.WHITE else Color.GRAY, 18)).growX().left()
        row.add((cost.tr() + stat.character).toLabel(if (allowed) Color.WHITE else Color.GRAY, 18)).right().padLeft(16f)
        if (allowed) {
            row.touchable = Touchable.enabled
            row.onClick {
                close()
                cityScreen.selectedQueueEntry = -1 // buying from this list never targets a queue slot
                cityScreen.selectConstruction(construction)
                buyButtonFactory.buyFromGamepad(construction, stat)
            }
        } else row.touchable = Touchable.disabled
        return row
    }
}

/** Gamepad X on a production queue entry */
internal class GamepadQueueEntryPopup(
    private val cityScreen: CityScreen,
    private val index: Int
) : Popup(cityScreen) {

    init {
        val cityView = cityScreen.cityView
        val queue = cityView.constructions.constructionQueue
        val name = queue.getOrNull(index)
        if (name != null) {
            val canChange = cityScreen.canCityBeChanged()
            addGoodSizedLabel(name, 24).padBottom(10f).row()
            addMenuButton("Move up", canChange && index > 0) {
                cityView.tryRaisePriority(index)
                afterChange()
            }
            addMenuButton("Move down", canChange && index < queue.lastIndex) {
                cityView.tryLowerPriority(index)
                afterChange()
            }
            addMenuButton("Remove from queue", canChange) {
                cityView.tryRemoveFromQueue(index, false)
                cityScreen.clearSelection()
                afterChange()
            }
            addCloseButton()
            open()
        }
    }

    private fun afterChange() {
        cityScreen.cityView.tryReassignPopulation()
        cityScreen.updateAsync()
    }
}

/** Gamepad X anywhere else in the city screen: city-wide actions */
internal class GamepadCityActionsPopup(private val cityScreen: CityScreen) : Popup(cityScreen) {

    init {
        val cityView = cityScreen.cityView
        val canChange = cityScreen.canChangeState
        addGoodSizedLabel(cityView.name, 24).padBottom(10f).row()

        addMenuButton("Rename city", canChange) {
            CityRenamePopup(
                screen = cityScreen,
                cityView = cityView,
                actionOnClose = { cityScreen.game.replaceCurrentScreen { CityScreen(cityView) } }
            )
        }

        val canAnnex = !cityView.viewingCiv().hasUnique(UniqueType.MayNotAnnexCities)
        when {
            cityView.isPuppet() && canAnnex -> addMenuButton("Annex city", canChange) {
                cityView.tryAnnexCity()
                cityScreen.updateAsync()
            }
            !cityView.isBeingRazed() -> addMenuButton("Raze city", canChange && cityView.canBeDestroyed() && canAnnex) {
                cityView.trySetRazing(true)
                cityScreen.updateAsync()
            }
            else -> addMenuButton("Stop razing city", canChange) {
                cityView.trySetRazing(false)
                cityScreen.updateAsync()
            }
        }

        addCloseButton()
        open()
    }
}
