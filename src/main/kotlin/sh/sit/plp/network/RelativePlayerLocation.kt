package sh.sit.plp.network

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import org.joml.Vector3f
import java.util.*

data class RelativePlayerLocation(
    /** UUID of the player */
    val playerUuid: UUID,
    /** Normalized direction vector where the player id */
    val direction: Vector3f,
    /** Distance in blocks, zero if disabled by config */
    val distance: Float,
    /** Mark color in 0xRRGGBB format. -1 for ColorMode.PLAYER_HEAD */
    val color: Int,
) {
    companion object {
        val CODEC: PacketCodec<PacketByteBuf, RelativePlayerLocation> =
            PacketCodec.of(RelativePlayerLocation::write, ::RelativePlayerLocation)
    }

    constructor(buf: PacketByteBuf) : this(
        playerUuid = buf.readUuid(),
        direction = buf.readVector3f(),
        distance = buf.readFloat(),
        color = buf.readInt(),
    )

    fun write(buf: PacketByteBuf) {
        buf.writeUuid(playerUuid)
        buf.writeVector3f(direction)
        buf.writeFloat(distance)
        buf.writeInt(color)
    }
}
