package sh.sit.plp.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import org.joml.Vector3f
import java.util.*

data class RelativePlayerLocation(
    /** UUID of the player */
    val playerUuid: UUID,
    /** Normalized direction vector where the player id */
    val direction: Vector3f,
    /** Distance in blocks, zero if disabled by config */
    val distance: Float,
    /** Mark color in 0xRRGGBB format */
    val color: Int,
) {
    companion object {
        val CODEC: StreamCodec<FriendlyByteBuf, RelativePlayerLocation> =
            StreamCodec.ofMember(RelativePlayerLocation::write, ::RelativePlayerLocation)
    }

    constructor(buf: FriendlyByteBuf) : this(
        playerUuid = buf.readUUID(),
        direction = buf.readVector3f(),
        distance = buf.readFloat(),
        color = buf.readInt(),
    )

    fun write(buf: FriendlyByteBuf) {
        buf.writeUUID(playerUuid)
        buf.writeVector3f(direction)
        buf.writeFloat(distance)
        buf.writeInt(color)
    }
}
