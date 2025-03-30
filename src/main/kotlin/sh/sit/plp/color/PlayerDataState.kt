package sh.sit.plp.color

import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.RegistryWrapper
import net.minecraft.server.MinecraftServer
import net.minecraft.world.PersistentState
import sh.sit.plp.PlayerLocatorPlus
import java.util.*

class PlayerDataState() : PersistentState() {
    companion object {
        private val TYPE = Type(
            ::PlayerDataState,
            ::PlayerDataState,
            null,
        )

        fun of(server: MinecraftServer): PlayerDataState {
            return server.overworld.persistentStateManager.getOrCreate(
                TYPE,
                "${PlayerLocatorPlus.MOD_ID}-player_data",
            )
        }
    }

    private val players = hashMapOf<UUID, PlayerData>()

    @Suppress("UNUSED_PARAMETER")
    constructor(nbt: NbtCompound, registryLookup: RegistryWrapper.WrapperLookup) : this() {
        val playersNbt = nbt.getCompound("players")
        playersNbt.keys.forEach { k ->
            val playerNbt = playersNbt.getCompound(k)
            val playerData = PlayerData(
                customColor = playerNbt.getInt("customColor")
            )
            players[UUID.fromString(k)] = playerData
        }
    }

    override fun writeNbt(nbt: NbtCompound, registries: RegistryWrapper.WrapperLookup): NbtCompound {
        val ret = NbtCompound()
        players.forEach { (k, v) ->
            val playerNbt = NbtCompound()
            playerNbt.putInt("customColor", v.customColor)
            ret.put(k.toString(), playerNbt)
        }
        nbt.put("players", ret)

        return nbt
    }

    fun getPlayer(uuid: UUID): PlayerData {
        return players.getOrPut(uuid) {
            markDirty()
            PlayerData()
        }
    }
}
