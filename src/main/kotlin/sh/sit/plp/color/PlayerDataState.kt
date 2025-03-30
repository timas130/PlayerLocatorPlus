package sh.sit.plp.color

import net.minecraft.nbt.NbtCompound
import net.minecraft.server.MinecraftServer
import net.minecraft.world.PersistentState
import net.minecraft.world.PersistentStateType
import sh.sit.plp.PlayerLocatorPlus
import java.util.*
import kotlin.jvm.optionals.getOrNull

class PlayerDataState : PersistentState() {
    companion object {
        private val CODEC = NbtCompound.CODEC
            .fieldOf("players")
            .xmap({ playersNbt ->
                val ret = hashMapOf<UUID, PlayerData>()
                playersNbt.keys.forEach { k ->
                    val playerNbt = playersNbt.getCompound(k).getOrNull()
                    val playerData = PlayerData(
                        customColor = playerNbt?.getInt("customColor")?.orElse(0xFFFFFF) ?: 0xFFFFFF,
                    )
                    ret[UUID.fromString(k)] = playerData
                }
                PlayerDataState().also {
                    it.players = ret
                }
            }, { state ->
                NbtCompound().also { ret ->
                    state.players.forEach { (k, v) ->
                        val playerNbt = NbtCompound()
                        playerNbt.putInt("customColor", v.customColor)
                        ret.put(k.toString(), playerNbt)
                    }
                }
            })
            .codec()

        private val TYPE = PersistentStateType(
            "${PlayerLocatorPlus.MOD_ID}-player_data",
            ::PlayerDataState,
            CODEC,
            null,
        )

        fun of(server: MinecraftServer): PlayerDataState {
            return server.overworld.persistentStateManager.getOrCreate(TYPE)
        }
    }

    private var players = hashMapOf<UUID, PlayerData>()

    fun getPlayer(uuid: UUID): PlayerData {
        return players.getOrPut(uuid) {
            markDirty()
            PlayerData()
        }
    }
}
