package sh.sit.plp

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import sh.sit.plp.PlayerLocatorPlus.config
import sh.sit.plp.color.PlayerDataState
import sh.sit.plp.config.ModConfig
import sh.sit.plp.network.PlayerLocationsS2CPayload
import sh.sit.plp.network.RelativePlayerLocation
import sh.sit.plp.util.ColorUtils
import java.util.*
import kotlin.math.round
import kotlin.random.Random

object BarUpdater {
    private data class StoredPlayerPosition(
        val pos: Vec3,
        val world: Level,
        val color: Int,
    ) {
        constructor(player: ServerPlayer) : this(player.position(), player.level(), calculateColor(player))

        companion object {
            fun calculateColor(player: ServerPlayer): Int {
                return when (config.colorMode) {
                    ModConfig.ColorMode.UUID -> ColorUtils.uuidToColor(player.uuid)
                    ModConfig.ColorMode.TEAM_COLOR -> player.teamColor
                    ModConfig.ColorMode.CUSTOM -> config.constantColor
                    ModConfig.ColorMode.CONSTANT -> PlayerDataState.of(player.level().server)
                        ?.getPlayer(player.uuid)?.customColor ?: 0
                }
            }
        }
    }

    private var previousPositions = mapOf<UUID, StoredPlayerPosition>()

    fun reset() {
        previousPositions = mapOf()
    }

    fun fullResend(server: MinecraftServer) {
        server.playerList.players.forEach {
            fullResend(it)
        }
    }

    fun fullResend(player: ServerPlayer) {
        val playerList = player.level().players()

        val relativePositions = playerList.mapNotNull {
            if (it == player) return@mapNotNull null

            val distance = player.position().distanceTo(it.position()).toFloat()
            if (config.maxDistance != 0 && distance > config.maxDistance) return@mapNotNull null

            calculateRelativeLocation(it.uuid, StoredPlayerPosition(player), StoredPlayerPosition(it))
        }

        ServerPlayNetworking.send(
            player,
            PlayerLocationsS2CPayload(
                locationUpdates = if (config.enabled) {
                    relativePositions
                } else {
                    listOf()
                },
                removeUuids = emptyList(),
                fullReset = true
            )
        )
    }

    fun update(server: MinecraftServer) {
        if (!config.enabled) return

        val currentPositions = getPositions(server)

        // Send the update packets:
        for (player in server.playerList.players) {
            val previousPlayer = previousPositions[player.uuid]

            val maxDistance = config.maxDistance

            val removeUuids = mutableSetOf<UUID>()
            for ((uuid, prevPos) in previousPositions) {
                if (uuid == player.uuid) continue

                val curPos = currentPositions[uuid]

                // If the player left our current dimension or the Game
                if (
                    prevPos.world != curPos?.world &&
                    player.level() == prevPos.world
                ) {
                    // ... remove them from the bar
                    removeUuids.add(uuid)
                }

                // If we left the dimension the other player was in
                if (
                    previousPlayer?.world != player.level() &&
                    previousPlayer?.world == curPos?.world
                ) {
                    // ... remove them from the bar
                    removeUuids.add(uuid)
                }

                // If the player is now farther than maxDistance
                if (
                    curPos != null &&
                    previousPlayer != null &&
                    curPos.world == player.level() &&
                    curPos.world == prevPos.world &&
                    maxDistance != 0
                ) {
                    val previousDistance = previousPlayer.pos.distanceTo(prevPos.pos)
                    val currentDistance = player.position().distanceTo(curPos.pos)

                    if (currentDistance > maxDistance && previousDistance <= maxDistance) {
                        // ... remove them from the bar
                        removeUuids.add(uuid)
                    }
                }
            }

            val updatedPositions = mutableListOf<RelativePlayerLocation>()

            for ((uuid, curPos) in currentPositions) {
                // don't update ourselves
                if (uuid == player.uuid) continue

                // don't update if different dimensions
                if (curPos.world != player.level()) continue

                val previousRelativeLocation = previousPositions[uuid]?.let { prevPos ->
                    previousPlayer?.let { prevPlayer ->
                        calculateRelativeLocation(uuid, prevPlayer, prevPos)
                    }
                }
                val currentRelativeLocation = calculateRelativeLocation(uuid, StoredPlayerPosition(player), curPos)

                // don't update if no changes (or not significant enough)
                if (previousRelativeLocation == currentRelativeLocation) continue

                // don't update position if we're too far
                val currentDistance = player.position().distanceTo(curPos.pos)
                if (maxDistance != 0 && currentDistance > maxDistance) continue

                updatedPositions.add(currentRelativeLocation)
            }

            val fullReset = previousPlayer?.world != player.level()

            if (updatedPositions.isEmpty() && removeUuids.isEmpty()) continue

            ServerPlayNetworking.send(
                player, PlayerLocationsS2CPayload(
                    locationUpdates = updatedPositions,
                    removeUuids = removeUuids.toList(),
                    fullReset = fullReset,
                )
            )
        }

        previousPositions = currentPositions
    }

    private fun getPositions(server: MinecraftServer): Map<UUID, StoredPlayerPosition> {
        return server.playerList.players
            .filterNot {
                (config.sneakingHides && it.isCrouching) ||
                        (config.pumpkinHides && !LivingEntity.PLAYER_NOT_WEARING_DISGUISE_ITEM.test(it)) ||
                        (config.mobHeadsHide && it.getItemBySlot(EquipmentSlot.HEAD)
                            .`is`(PlayerLocatorPlus.HIDING_EQUIPMENT_TAG)) ||
                        (config.invisibilityHides && it.hasEffect(MobEffects.INVISIBILITY)) ||
                        it.isSpectator
            }
            .associate { it.uuid to StoredPlayerPosition(it) }
    }

    private fun calculateRelativeLocation(
        uuid: UUID,
        selfPos: StoredPlayerPosition,
        otherPos: StoredPlayerPosition
    ): RelativePlayerLocation {
        val direction = selfPos.pos.cross(otherPos.pos).normalize().toVector3f()
        direction.x = round(direction.x * config.directionPrecision) / config.directionPrecision
        direction.y = round(direction.y * config.directionPrecision) / config.directionPrecision
        direction.z = round(direction.z * config.directionPrecision) / config.directionPrecision

        return RelativePlayerLocation(
            playerUuid = uuid,
            direction = direction,
            distance = if (config.sendDistance) {
                val distance = selfPos.pos.distanceTo(otherPos.pos).toFloat()
                if (distance < 200) distance
                else round(distance / 50) * 50
            } else {
                0f
            },
            color = otherPos.color,
        )
    }

    fun sendFakePlayers(player: ServerPlayer) {
        val positions = (0..5)
            .map {
                RelativePlayerLocation(
                    playerUuid = UUID.randomUUID(),
                    direction = Vector3f(Random.nextFloat(), Random.nextFloat() * 0.75f, Random.nextFloat()),
                    distance = if (config.sendDistance) Random.nextFloat() * 750f else 0f,
                    color = ColorUtils.uuidToColor(UUID.randomUUID()),
                )
            }

        ServerPlayNetworking.send(
            player,
            PlayerLocationsS2CPayload(
                locationUpdates = positions,
                removeUuids = emptyList(),
                fullReset = false,
            )
        )
    }
}
