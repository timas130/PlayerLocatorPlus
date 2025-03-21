package sh.sit.plp

import me.shedaniel.autoconfig.ConfigData
import me.shedaniel.autoconfig.annotation.Config
import me.shedaniel.autoconfig.annotation.ConfigEntry

@Config(name = PlayerLocatorPlus.MOD_ID)
class ModConfig : ConfigData {
    @ConfigEntry.Category("general")
    var enabled = true
    @ConfigEntry.Category("general")
    @ConfigEntry.Gui.Tooltip
    var sendDistance = true
    @ConfigEntry.Category("general")
    @ConfigEntry.Gui.Tooltip
    var maxDistance = 0
    @ConfigEntry.Category("general")
    @ConfigEntry.Gui.Tooltip
    var directionPrecision = 300f
    @ConfigEntry.Category("general")
    @ConfigEntry.Gui.Tooltip
    var ticksBetweenUpdates = 10
    @ConfigEntry.Category("general")
    var sneakingHides = true
    @ConfigEntry.Category("general")
    var pumpkinHides = true
    @ConfigEntry.Category("general")
    var invisibilityHides = true

    @ConfigEntry.Category("style")
    @ConfigEntry.Gui.Tooltip
    var visible = true
    @ConfigEntry.Category("style")
    @ConfigEntry.Gui.Tooltip
    var fadeMarkers = true
    @ConfigEntry.Category("style")
    @ConfigEntry.Gui.Tooltip
    var showHeight = true
}
