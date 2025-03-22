package sh.sit.plp.network

import net.minecraft.network.PacketByteBuf.PacketReader
import net.minecraft.network.PacketByteBuf.PacketWriter
import org.joml.Vector3f
import java.util.*

data class RelativePlayerLocation(
    /** UUID of the player */
    val playerUuid: UUID,
    /** Normalized direction vector where the player id */
    val direction: Vector3f,
    /** Distance in blocks, zero if disabled by config */
    val distance: Float,
) {
    companion object {
        val WRITER = PacketWriter<RelativePlayerLocation> { buf, self ->
            buf.writeUuid(self.playerUuid)
            buf.writeVector3f(self.direction)
            buf.writeFloat(self.distance)
        }

        val READER = PacketReader { buf ->
            RelativePlayerLocation(
                playerUuid = buf.readUuid(),
                direction = buf.readVector3f(),
                distance = buf.readFloat(),
            )
        }
    }
}
