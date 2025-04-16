package sh.sit.plp.config

import com.akuleshov7.ktoml.annotations.TomlInteger
import com.akuleshov7.ktoml.writers.IntegerRepresentation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.shedaniel.autoconfig.ConfigData
import me.shedaniel.autoconfig.annotation.Config
import me.shedaniel.autoconfig.annotation.ConfigEntry
import me.shedaniel.clothconfig2.gui.entries.SelectionListEntry.Translatable
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import sh.sit.plp.PlayerLocatorPlus

@Config(name = PlayerLocatorPlus.MOD_ID)
@Serializable
class ModConfig : ConfigData {
    var enabled = true
    @ConfigEntry.Gui.Tooltip
    var sendServerConfig = true
    @ConfigEntry.Gui.Tooltip
    var sendDistance = true
    @ConfigEntry.Gui.Tooltip
    var maxDistance = 0
    @ConfigEntry.Gui.Tooltip
    var directionPrecision = 300f
    @ConfigEntry.Gui.Tooltip
    var ticksBetweenUpdates = 5
    var sneakingHides = true
    var pumpkinHides = true
    @ConfigEntry.Gui.Tooltip
    var mobHeadsHide = true
    var invisibilityHides = true

    @ConfigEntry.Category("style")
    @ConfigEntry.Gui.Tooltip
    var visible = true
    @ConfigEntry.Category("style")
    var visibleEmpty = false
    @ConfigEntry.Category("style")
    var acceptServerConfig = true
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
    @ConfigEntry.Category("style")
    @ConfigEntry.Gui.Tooltip
    var showHeadsOnTab = true
    @ConfigEntry.Category("style")
    @ConfigEntry.Gui.Tooltip
    var showNamesOnTab = true

    @ConfigEntry.Category("color")
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    var colorMode = ColorMode.UUID
    @ConfigEntry.Category("color")
    @ConfigEntry.ColorPicker
    @ConfigEntry.Gui.Tooltip
    @TomlInteger(IntegerRepresentation.HEX)
    var constantColor = 0xFFFFFF

    enum class ColorMode(private val key: String) : Translatable {
        UUID("uuid"),
        TEAM_COLOR("team_color"),
        CUSTOM("custom"),
        CONSTANT("constant");

        override fun getKey(): String {
            return "text.autoconfig.player-locator-plus.option.colorMode.$key"
        }
    }

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

    companion object {
        val PACKET_CODEC = object : PacketCodec<PacketByteBuf, ModConfig> {
            val json = Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
            }

            override fun encode(buf: PacketByteBuf, value: ModConfig) {
                buf.writeString(json.encodeToString(value), 16 * 1024)
            }

            override fun decode(buf: PacketByteBuf): ModConfig {
                val data = buf.readString(16 * 1024)
                return json.decodeFromString(data)
            }
        }
    }
}
