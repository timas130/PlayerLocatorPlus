package sh.sit.plp

import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.ActionResult
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import org.slf4j.LoggerFactory
import sh.sit.plp.network.PlayerLocationsS2CPayload
import sh.sit.plp.network.RelativePlayerLocation
import java.util.*

object PlayerLocatorPlus : ModInitializer {
    const val MOD_ID = "player-locator-plus"
    val logger = LoggerFactory.getLogger("player-locator-plus")

    data class StoredPlayerPosition(
        val pos: Vec3d,
        val world: World,
    )

    private var previousPlayerPositions: Map<UUID, StoredPlayerPosition> = mapOf()
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

        PayloadTypeRegistry.playS2C().register(PlayerLocationsS2CPayload.ID, PlayerLocationsS2CPayload.CODEC)

        // Basically how this works:
        // 1) We resend all players unconditionally when:
        //   a) a player joins the game
        //   b) a player changes worlds (dimensions)
        // 2) Every 5 (UPDATE_EVERY_TICKS) server ticks:
        //   a) We record which players changed their positions or dimensions
        //   b) Their updated relative positions are broadcast to everyone

        ServerPlayConnectionEvents.JOIN.register(ServerPlayConnectionEvents.Join { handler, _, _ ->
            fullResend(handler.player)
        })
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
            ServerEntityWorldChangeEvents.AfterPlayerChange { player, _, _ ->
                fullResend(player)
            }
        )

        ServerTickEvents.END_SERVER_TICK.register(ServerTickEvents.EndTick { server ->
            if (tickCounter < config.ticksBetweenUpdates) {
                tickCounter++
                return@EndTick
            }
            tickCounter = 0

            val currentPlayerPositions = server.playerManager.playerList
                .associateTo(mutableMapOf()) { it.uuid to StoredPlayerPosition(it.pos, it.world) }

            val removeUuids = mutableListOf<UUID>()

            val sneakyPlayers = mutableListOf<UUID>()

            val changedPlayerPositions = mutableMapOf<UUID, StoredPlayerPosition>()
            val playerMovement = mutableMapOf<UUID, Vec3d>()
            for (player in currentPlayerPositions) {
                val previous = previousPlayerPositions[player.key]
                if (previous == null || previous.pos != player.value.pos || previous.world != player.value.world) {
                    changedPlayerPositions[player.key] = player.value
                }
                if (previous != null && previous.pos != player.value.pos && previous.world == player.value.world) {
                    playerMovement[player.key] = player.value.pos.subtract(previous.pos)
                }
                if (previous != null && previous.world != player.value.world) {
                    removeUuids.add(player.key)
                }

                val actualPlayer = server.playerManager.getPlayer(player.key)!!
                if (
                    (config.sneakingHides && actualPlayer.isSneaking) ||
                    (config.pumpkinHides && !LivingEntity.NOT_WEARING_GAZE_DISGUISE_PREDICATE.test(actualPlayer)) ||
                    (config.invisibilityHides && actualPlayer.hasStatusEffect(StatusEffects.INVISIBILITY))
                ) {
                    sneakyPlayers.add(player.key)
                }
            }

            for (player in sneakyPlayers) {
                currentPlayerPositions.remove(player)
                changedPlayerPositions.remove(player)
            }

            for (prevPlayer in previousPlayerPositions) {
                if (!currentPlayerPositions.containsKey(prevPlayer.key)) {
                    removeUuids.add(prevPlayer.key)
                }
            }

            broadcastPositions(server, changedPlayerPositions, currentPlayerPositions, playerMovement, removeUuids)

            previousPlayerPositions = currentPlayerPositions
        })
        ServerLifecycleEvents.SERVER_STOPPED.register(ServerLifecycleEvents.ServerStopped {
            previousPlayerPositions = mapOf()
        })

        logger.info("hi!")
    }

    private fun fullResend(player: ServerPlayerEntity) {
        val playerList = player.serverWorld.players
        val ownPosition = player.pos

        val relativePositions = playerList.mapNotNull {
            if (it == player) return@mapNotNull null

            val distance = ownPosition.distanceTo(it.pos).toFloat()
            if (distance > config.maxDistance && config.maxDistance != 0) return@mapNotNull null

            RelativePlayerLocation(
                playerUuid = it.uuid,
                direction = ownPosition.relativize(it.pos).normalize().toVector3f(),
                distance = if (config.sendDistance) { distance } else { 0f },
            )
        }

        ServerPlayNetworking.send(
            player,
            PlayerLocationsS2CPayload(
                locationUpdates = relativePositions,
                removeUuids = emptyList(),
                fullReset = true
            )
        )
    }

    private fun broadcastPositions(
        server: MinecraftServer,
        changedPlayerPositions: Map<UUID, StoredPlayerPosition>,
        currentPlayerPositions: Map<UUID, StoredPlayerPosition>,
        playerMovement: Map<UUID, Vec3d>,
        removeUuids: List<UUID>,
    ) {
        for (player in server.playerManager.playerList) {
            val selfUpdated = changedPlayerPositions.containsKey(player.uuid)
            // if the destination player moved, we need to resend all players in the world
            // if they haven't moved, we only need to resend players who have moved
            val others = if (selfUpdated) {
                currentPlayerPositions.filter { it.value.world == player.world }
            } else {
                changedPlayerPositions.filter { it.value.world == player.world }
            }

            val additionalRemoveUuids = mutableListOf<UUID>()

            val locationUpdates = others.mapNotNull { update ->
                if (update.key == player.uuid) return@mapNotNull null
                val distance = player.pos.distanceTo(update.value.pos).toFloat()
                val previousDistance = update.value.pos.subtract(playerMovement[update.key] ?: Vec3d.ZERO)
                    .distanceTo(player.pos.subtract(playerMovement[player.uuid] ?: Vec3d.ZERO))
                if (distance > config.maxDistance && config.maxDistance != 0) {
                    if (previousDistance <= config.maxDistance) {
                        additionalRemoveUuids.add(update.key)
                    }
                    return@mapNotNull null
                }

                RelativePlayerLocation(
                    playerUuid = update.key,
                    direction = player.pos.relativize(update.value.pos).normalize().toVector3f(),
                    distance = if (config.sendDistance) { distance } else { 0f }
                )
            }

            val actualRemoveUuids = removeUuids + additionalRemoveUuids
            if (locationUpdates.isEmpty() && actualRemoveUuids.isEmpty()) continue

            ServerPlayNetworking.send(
                player,
                PlayerLocationsS2CPayload(
                    locationUpdates = locationUpdates,
                    removeUuids = actualRemoveUuids,
                    fullReset = false
                )
            )
        }
    }
}
