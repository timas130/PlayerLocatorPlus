package sh.sit.plp.network

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import sh.sit.plp.PlayerLocatorPlus
import java.util.*

@JvmRecord
data class PlayerLocationsS2CPayload(
    val locationUpdates: List<RelativePlayerLocation>,
    val removeUuids: List<UUID>,
    val fullReset: Boolean,
) : CustomPayload {
    companion object {
        private val PLAYER_LOCATIONS_PAYLOAD_ID = Identifier.of(PlayerLocatorPlus.MOD_ID, "player_locations_v2")

        val ID = CustomPayload.Id<PlayerLocationsS2CPayload>(PLAYER_LOCATIONS_PAYLOAD_ID)
        val CODEC: PacketCodec<PacketByteBuf, PlayerLocationsS2CPayload> = PacketCodec.tuple(
            PacketCodecs.collection(
                /* factory = */ { capacity -> ArrayList(capacity) },
                /* elementCodec = */ RelativePlayerLocation.CODEC
            ),
            PlayerLocationsS2CPayload::locationUpdates,
            PacketCodecs.collection(
                { capacity -> ArrayList(capacity) },
                Uuids.PACKET_CODEC
            ),
            PlayerLocationsS2CPayload::removeUuids,
            PacketCodecs.BOOL,
            PlayerLocationsS2CPayload::fullReset,
            ::PlayerLocationsS2CPayload
        )
    }

    override fun getId(): CustomPayload.Id<out CustomPayload> = ID
}
