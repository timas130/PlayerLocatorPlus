package sh.sit.plp.network

import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.networking.v1.FabricPacket
import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import net.minecraft.util.Identifier
import sh.sit.plp.ModConfig
import sh.sit.plp.PlayerLocatorPlus

@JvmRecord
data class ModConfigS2CPayload(
    val config: ModConfig,
) : FabricPacket {
    companion object {
        val ID = Identifier.of(PlayerLocatorPlus.MOD_ID, "mod_config")

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        val TYPE = PacketType.create(ID) { buf ->
            ModConfigS2CPayload(
                config = json.decodeFromString(buf.readString(16 * 1024))
            )
        }
    }

    override fun getType(): PacketType<ModConfigS2CPayload> = TYPE

    override fun write(buf: PacketByteBuf) {
        buf.writeString(json.encodeToString(config), 16 * 1024)
    }
}
