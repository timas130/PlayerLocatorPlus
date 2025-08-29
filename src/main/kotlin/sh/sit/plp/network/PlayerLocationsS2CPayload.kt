package sh.sit.plp.network

import net.fabricmc.fabric.api.networking.v1.FabricPacket
import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import net.minecraft.util.Identifier
import sh.sit.plp.PlayerLocatorPlus
import java.util.*

@JvmRecord
data class PlayerLocationsS2CPayload(
    val locationUpdates: List<RelativePlayerLocation>,
    val removeUuids: List<UUID>,
    val fullReset: Boolean,
) : FabricPacket {
    companion object {
        val ID = Identifier.of(PlayerLocatorPlus.MOD_ID, "player_locations_v2")

        val TYPE = PacketType.create(ID) { buf ->
            PlayerLocationsS2CPayload(
                locationUpdates = buf.readCollection(
                    { capacity -> ArrayList(capacity) },
                    RelativePlayerLocation.READER,
                ),
                removeUuids = buf.readCollection(
                    { capacity -> ArrayList(capacity) },
                    PacketByteBuf::readUuid,
                ),
                fullReset = buf.readBoolean(),
            )
        }
    }

    override fun write(buf: PacketByteBuf) {
        buf.writeCollection(locationUpdates, RelativePlayerLocation.WRITER)
        buf.writeCollection(removeUuids, PacketByteBuf::writeUuid)
        buf.writeBoolean(fullReset)
    }

    override fun getType(): PacketType<PlayerLocationsS2CPayload> {
        return TYPE
    }
}
