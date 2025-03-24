package sh.sit.plp

import com.mojang.brigadier.Command
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.server.command.CommandManager
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory

object PlayerLocatorPlus : ModInitializer {
    const val MOD_ID = "player-locator-plus"
    val logger = LoggerFactory.getLogger("player-locator-plus")

    val HIDING_EQUIPMENT_TAG = TagKey.of(RegistryKeys.ITEM, Identifier.of("player-locator-plus", "hiding_equipment"))!!

    private var tickCounter = 0

    val config get() = ConfigManager.getConfig()

    override fun onInitialize() {
        ConfigManager.init()

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

        CommandRegistrationCallback.EVENT.register(CommandRegistrationCallback { dispatcher, _, _ ->
            dispatcher.register(CommandManager.literal("plp")
                .then(CommandManager.literal("reload")
                    .executes { c ->
                        c.source.sendFeedback({ Text.literal("Player Locator config reloaded") }, false)
                        ConfigManager.reload(fromDisk = true)
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
