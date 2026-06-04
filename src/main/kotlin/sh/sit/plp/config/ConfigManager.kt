package sh.sit.plp.config

import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.ConfigHolder
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.players.PlayerList
import net.minecraft.world.InteractionResult
import sh.sit.plp.PlayerLocatorPlus
import sh.sit.plp.network.ModConfigS2CPayload

object ConfigManager {
    private lateinit var config: ModConfig
    var configOverride: ModConfig? = null
        set(value) {
            if (value != null) {
                configHolder.config = value
            } else {
                configHolder.config = config
            }
            field = value
        }

    private lateinit var configHolder: ConfigHolder<ModConfig>

    private var playerList: PlayerList? = null

    fun init() {
        AutoConfig.register(ModConfig::class.java, ::KTomlConfigSerializer)
        configHolder = AutoConfig.getConfigHolder(ModConfig::class.java)
        configHolder.registerSaveListener { _, modConfig ->
            config = modConfig
            reload()
            InteractionResult.PASS
        }
        configHolder.registerLoadListener { _, modConfig ->
            config = modConfig
            reload()
            InteractionResult.PASS
        }
        config = configHolder.config


        ServerTickEvents.END_SERVER_TICK.register(ServerTickEvents.EndTick { server ->
            playerList = server.playerList
        })

        ServerPlayConnectionEvents.JOIN.register(ServerPlayConnectionEvents.Join { handler, _, _ ->
            if (!config.sendServerConfig) return@Join
            ServerPlayNetworking.send(
                handler.player,
                ModConfigS2CPayload(config)
            )
        })
    }

    fun reload(fromDisk: Boolean = false) {
        if (fromDisk) {
            configHolder.load()
            config = configHolder.get()
        }

        if (!config.sendServerConfig) {
            PlayerLocatorPlus.logger.info("server config reloaded")
            return
        }
        for (player in playerList?.players ?: emptyList()) {
            ServerPlayNetworking.send(player, ModConfigS2CPayload(config))
        }

        PlayerLocatorPlus.logger.info("server config reloaded and resent to clients")
    }

    fun getConfig(): ModConfig {
        return configOverride?.takeIf { config.acceptServerConfig } ?: config
    }
}
