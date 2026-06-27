package sh.sit.plp

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory
import sh.sit.plp.color.PLPCommand
import sh.sit.plp.config.ConfigManager
import sh.sit.plp.network.ModConfigS2CPayload
import sh.sit.plp.network.PlayerLocationsS2CPayload

object PlayerLocatorPlus : ModInitializer {
    const val MOD_ID = "player-locator-plus"
    val logger = LoggerFactory.getLogger("player-locator-plus")!!

    val HIDING_EQUIPMENT_TAG = TagKey.of(RegistryKeys.ITEM, Identifier.of("player-locator-plus", "hiding_equipment"))!!

    private var tickCounter = 0

    val config get() = ConfigManager.getConfig()

    override fun onInitialize() {
        ConfigManager.init()

        PayloadTypeRegistry.playS2C().register(PlayerLocationsS2CPayload.ID, PlayerLocationsS2CPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ModConfigS2CPayload.ID, ModConfigS2CPayload.CODEC)

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

        PLPCommand.register()

        logger.info("hi!")
    }
}
