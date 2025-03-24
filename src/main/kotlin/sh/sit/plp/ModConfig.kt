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
    var ticksBetweenUpdates = 5
    @ConfigEntry.Category("general")
    var sneakingHides = true
    @ConfigEntry.Category("general")
    var pumpkinHides = true
    @ConfigEntry.Category("general")
    @ConfigEntry.Gui.Tooltip
    var mobHeadsHide = true
    @ConfigEntry.Category("general")
    var invisibilityHides = true

    @ConfigEntry.Category("style")
    @ConfigEntry.Gui.Tooltip
    var visible = true
    @ConfigEntry.Category("style")
    var visibleEmpty = false
    @ConfigEntry.Category("style")
    @ConfigEntry.Gui.Tooltip
    var fadeMarkers = true
    @ConfigEntry.Category("style")
    var fadeStart = 100
    @ConfigEntry.Category("style")
    var fadeEnd = 1000
    @ConfigEntry.Category("style")
    var fadeEndOpacity = 0.3f
    @ConfigEntry.Category("style")
    @ConfigEntry.Gui.Tooltip
    var showHeight = true

    override fun validatePostLoad() {
        if (fadeStart < 0) {
            PlayerLocatorPlus.logger.warn("invalid config: fadeStart < 0")
            fadeStart = 0
        }
        if (fadeEnd < 1 || fadeEnd <= fadeStart) {
            PlayerLocatorPlus.logger.warn("invalid config: fadeEnd < 1 or fadeEnd <= fadeStart")
            fadeEnd = fadeStart + 1
        }
        if (fadeEndOpacity < 0 || fadeEndOpacity > 1) {
            PlayerLocatorPlus.logger.warn("invalid config: fadeEndOpacity not in [0, 1]")
            fadeEndOpacity = 0.3f
        }
        if (ticksBetweenUpdates < 0) {
            PlayerLocatorPlus.logger.warn("invalid config: ticksBetweenUpdates < 0")
            ticksBetweenUpdates = 0
        }
        if (directionPrecision <= 1) {
            PlayerLocatorPlus.logger.warn("invalid config: directionPrecision <= 1")
            directionPrecision = 300f
        }
        if (maxDistance < 0) {
            PlayerLocatorPlus.logger.warn("invalid config: maxDistance < 0")
            maxDistance = 0
        }
    }
}
