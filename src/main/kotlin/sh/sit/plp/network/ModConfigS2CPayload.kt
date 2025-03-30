package sh.sit.plp.network

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import sh.sit.plp.config.ModConfig
import sh.sit.plp.PlayerLocatorPlus

@JvmRecord
data class ModConfigS2CPayload(
    val config: ModConfig,
) : CustomPayload {
    companion object {
        private val MOD_CONFIG_PAYLOAD_ID = Identifier.of(PlayerLocatorPlus.MOD_ID, "mod_config")

        val ID = CustomPayload.Id<ModConfigS2CPayload>(MOD_CONFIG_PAYLOAD_ID)
        val CODEC: PacketCodec<PacketByteBuf, ModConfigS2CPayload> = PacketCodec.tuple(
            ModConfig.PACKET_CODEC,
            ModConfigS2CPayload::config,
            ::ModConfigS2CPayload
        )
    }

    override fun getId(): CustomPayload.Id<out CustomPayload> = ID
}
