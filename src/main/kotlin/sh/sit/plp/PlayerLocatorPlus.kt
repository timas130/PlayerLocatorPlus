package sh.sit.plp

import com.mojang.brigadier.Command
import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.command.CommandManager
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import org.slf4j.LoggerFactory

object PlayerLocatorPlus : ModInitializer {
    const val MOD_ID = "player-locator-plus"
    val logger = LoggerFactory.getLogger("player-locator-plus")

    private var tickCounter = 0

    lateinit var config: ModConfig

    override fun onInitialize() {
        // config
        AutoConfig.register(ModConfig::class.java, ::Toml4jConfigSerializer)
        val configHolder = AutoConfig.getConfigHolder(ModConfig::class.java)
        config = configHolder.config
        configHolder.registerSaveListener { _, modConfig ->
            config = modConfig
            ActionResult.SUCCESS
        }
        configHolder.registerLoadListener { _, modConfig ->
            config = modConfig
            ActionResult.SUCCESS
        }

        ServerPlayConnectionEvents.JOIN.register(ServerPlayConnectionEvents.Join { handler, _, _ ->
            BarUpdater.fullResend(handler.player)
        })

        ServerTickEvents.END_SERVER_TICK.register(ServerTickEvents.EndTick { server ->
            if (tickCounter < config.ticksBetweenUpdates) {
                tickCounter++
                return@EndTick
            }
            tickCounter = 0

            BarUpdater.update(server)
        })
        ServerLifecycleEvents.SERVER_STOPPED.register(ServerLifecycleEvents.ServerStopped {
            BarUpdater.reset()
        })

        CommandRegistrationCallback.EVENT.register(CommandRegistrationCallback { dispatcher, registry, env ->
            dispatcher.register(CommandManager.literal("plp")
                .then(CommandManager.literal("reload")
                    .executes { c ->
                        c.source.sendFeedback({ Text.literal("Player Locator config reloaded") }, false)
                        configHolder.load()
                        config = configHolder.get()
                        BarUpdater.fullResend(c.source.server)
                        Command.SINGLE_SUCCESS
                    })
                .then(CommandManager.literal("random")
                    .executes { c ->
                        c.source.player?.let { BarUpdater.sendFakePlayers(it) }
                        Command.SINGLE_SUCCESS
                    }))
        })

        logger.info("hi!")
    }
}
