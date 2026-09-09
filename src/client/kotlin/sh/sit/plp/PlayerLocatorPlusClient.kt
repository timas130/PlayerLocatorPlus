package sh.sit.plp

import com.mojang.datafixers.util.Either
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import net.minecraft.util.profiling.Profiler
import net.minecraft.world.level.GameType
import org.joml.Vector2d
import org.joml.Vector3f
import sh.sit.plp.PlayerLocatorPlus.config
import sh.sit.plp.config.ConfigManagerClient
import sh.sit.plp.network.PlayerLocationsS2CPayload
import sh.sit.plp.network.RelativePlayerLocation
import sh.sit.plp.util.Animatable
import sh.sit.plp.util.MathUtils
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sqrt

object PlayerLocatorPlusClient : ClientModInitializer {
    private val EXPERIENCE_BAR_BACKGROUND_TEXTURE = Identifier.fromNamespaceAndPath(PlayerLocatorPlus.MOD_ID, "hud/empty_bar")
    private val PLAYER_MARK_TEXTURE = Identifier.fromNamespaceAndPath(PlayerLocatorPlus.MOD_ID, "hud/player_mark")
    private val PLAYER_MARK_UP_TEXTURE = Identifier.fromNamespaceAndPath(PlayerLocatorPlus.MOD_ID, "hud/player_mark_up")
    private val PLAYER_MARK_DOWN_TEXTURE = Identifier.fromNamespaceAndPath(PlayerLocatorPlus.MOD_ID, "hud/player_mark_down")
    private val PLAYER_MARK_WHITE_OUTLINE_TEXTURE = Identifier.fromNamespaceAndPath(PlayerLocatorPlus.MOD_ID, "hud/player_mark_white_outline")

    private val PLAYER_MARK_TEXTURES = arrayOf(
        Identifier.fromNamespaceAndPath(PlayerLocatorPlus.MOD_ID, "hud/player_mark_0"),
        Identifier.fromNamespaceAndPath(PlayerLocatorPlus.MOD_ID, "hud/player_mark_1"),
        Identifier.fromNamespaceAndPath(PlayerLocatorPlus.MOD_ID, "hud/player_mark_2"),
        Identifier.fromNamespaceAndPath(PlayerLocatorPlus.MOD_ID, "hud/player_mark_3"),
        Identifier.fromNamespaceAndPath(PlayerLocatorPlus.MOD_ID, "hud/player_mark_4"),
        Identifier.fromNamespaceAndPath(PlayerLocatorPlus.MOD_ID, "hud/player_mark_5"),
        Identifier.fromNamespaceAndPath(PlayerLocatorPlus.MOD_ID, "hud/player_mark_6"),
    )

    private const val NAME_PLAQUE_PADDING_X = 4
    private const val NAME_PLAQUE_PADDING_Y = 2
    private const val NAME_PLAQUE_MARGIN = 2
    private const val NAME_PLAQUE_OVERLAP_THRESHOLD = 2

    private const val HUD_OFFSET_TOTAL = 16f
    private var hudOffset = Animatable(0f)

    // for mixin
    val currentHudOffset get() = hudOffset.currentValue

    private data class MarkerPosition(
        val location: RelativePlayerLocation,
        // Local player position when this marker update was received. Used to compensate
        // for local movement until the next update. Null means the direction is already live.
        // (Vanilla waypoints are recalculated against the current camera every frame.)
        val projectionOrigin: Vec3?,
    )

    private val relativePositionsLock = ReentrantLock()
    private val relativePositions = mutableMapOf<UUID, MarkerPosition>()

    private data class NamePlaque(
        val x: Int,
        val playerName: String,
        val progress: Double
    )

    override fun onInitializeClient() {
        ConfigManagerClient.init()

        ClientPlayNetworking.registerGlobalReceiver(PlayerLocationsS2CPayload.ID) { payload, _ ->
            val projectionOrigin = Minecraft.getInstance().player?.position() ?: Vec3.ZERO

            relativePositionsLock.lock()
            if (payload.fullReset) {
                relativePositions.clear()
            } else {
                payload.removeUuids.forEach {
                    relativePositions.remove(it)
                }
            }

            for (update in payload.locationUpdates) {
                relativePositions[update.playerUuid] = MarkerPosition(update, projectionOrigin)
            }

            relativePositionsLock.unlock()
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            relativePositionsLock.lock()
            relativePositions.clear()
            relativePositionsLock.unlock()
        }
    }

    fun isBarVisible(): Boolean {
        val client = Minecraft.getInstance()

        val player = client.player ?: return false
        val interactionManager = client.gameMode ?: return false
        val hud = client.gui.hud
        val networkHandler = client.connection

        // hide when disabled
        if (!config.visible) {
            return false
        }
        // hide in F1
        if (hud.isHidden) {
            return false
        }
        // hide when there are no other players online and relativePositions is empty
        if (
            !config.visibleEmpty &&
            relativePositions.isEmpty() &&
            networkHandler?.onlinePlayers?.any { it.profile.id != player.uuid } != true &&
            getVanillaWaypoints(client).isEmpty()
        ) {
            return false
        }
        // hide in spectator mode when the spectator menu is not open
        if (
            interactionManager.playerMode == GameType.SPECTATOR &&
            !hud.spectatorGui.isMenuActive &&
            !config.alwaysVisibleInSpectator
        ) {
            return false
        }

        return true
    }

    fun render(context: GuiGraphicsExtractor, tickCounter: DeltaTracker) {
        if (!config.visible) return

        if (!isBarVisible()) return

        val client = Minecraft.getInstance()
        Profiler.get().push("plp")
        val player = client.player ?: return
        val interactionManager = client.gameMode ?: return

        val barWidth = 182
        val x = context.guiWidth() / 2 - 91
        val y = context.guiHeight() - 32 + 3

        val barRendered = player.jumpableVehicle() != null || interactionManager.hasExperience()
        if (!barRendered) {
            context.blitSprite(RenderPipelines.GUI_TEXTURED, EXPERIENCE_BAR_BACKGROUND_TEXTURE, x, y, barWidth, 5)
        }

        relativePositionsLock.lock()

        val namePlaques = mutableListOf<NamePlaque>()

        val isTabPressed = client.options.keyPlayerList.isDown

        for (marker in (relativePositions.values.asSequence() + getVanillaWaypoints(client))) {
            val position = marker.location
            val projectionOrigin = marker.projectionOrigin
            val playerMarker = player.level().getEntity(position.playerUuid)
            val actualPosition = playerMarker
                ?.getPosition(tickCounter.getGameTimeDeltaPartialTick(false))
            val direction = if (actualPosition != null) {
                actualPosition.subtract(player.getPosition(tickCounter.getGameTimeDeltaPartialTick(false)))
            } else if (projectionOrigin == null || position.distance == 0f) {
                Vec3(position.direction)
            } else {
                val projectedPosition = projectionOrigin
                    .add(Vec3(position.direction).scale(position.distance.toDouble()))
                projectedPosition.subtract(player.getPosition(tickCounter.getGameTimeDeltaPartialTick(false)))
            }

            val direction2d = Vector2d(direction.x, direction.z)
            if (!direction2d.isFinite) {
                continue
            }
            val rotationVec = player.getViewVector(tickCounter.getGameTimeDeltaPartialTick(false))
            var relativeAngle = -direction2d.angle(Vector2d(rotationVec.x, rotationVec.z)) * 180.0 / Math.PI
            if (relativeAngle.isNaN()) {
                relativeAngle = 0.0
            }

            val horizontalFov = MathUtils.calculateHorizontalFov(
                verticalFov = client.options.fov().get(),
                width = context.guiWidth(),
                height = context.guiHeight()
            )
            val progress = (relativeAngle + horizontalFov / 2) / horizontalFov
            if (progress !in 0.0..1.0) {
                continue
            }

            val markX = x + (progress * barWidth.toFloat()).roundToInt() - 4

            val showHeadIcon = config.alwaysShowHeads || (config.showHeadsOnTab && isTabPressed)

            val playerList = client.connection?.onlinePlayers ?: emptyList()
            val playerListEntry = playerList.find { it.profile.id == position.playerUuid }

            val opacity = if (config.fadeMarkers) {
                val dist = position.distance.coerceIn(config.fadeStart.toFloat(), config.fadeEnd.toFloat())
                val fadeProgress = 1 - (dist - config.fadeStart) / (config.fadeEnd - config.fadeStart)
                (((1 - config.fadeEndOpacity) * fadeProgress + config.fadeEndOpacity) * 255).roundToInt()
            } else {
                255
            }
            val color = (opacity shl 24) or (position.color and 0xFFFFFF)

            // store marker information for name plaque rendering later
            if (playerListEntry != null && config.showNamesOnTab) {
                namePlaques.add(
                    NamePlaque(
                        x = markX,
                        playerName = playerListEntry.profile.name,
                        progress = progress
                    )
                )
            }

            if (playerListEntry == null || !showHeadIcon) {
                val texture = if (config.shrinkMarkers) {
                    val dist = position.distance.coerceIn(config.shrinkStart.toFloat(), config.shrinkEnd.toFloat())
                    val shrinkProgress = (dist - config.shrinkStart) / (config.shrinkEnd - config.shrinkStart)
                    val textureIdx = (shrinkProgress * PLAYER_MARK_TEXTURES.size).toInt()
                        .coerceAtMost(PLAYER_MARK_TEXTURES.size - 1)
                    PLAYER_MARK_TEXTURES[textureIdx]
                } else {
                    PLAYER_MARK_TEXTURE
                }

                context.blitSprite(
                    /* renderPipeline = */ RenderPipelines.GUI_TEXTURED,
                    /* location = */ texture,
                    /* x = */ markX,
                    /* y = */ y - 1,
                    /* width = */ 7,
                    /* height = */ 7,
                    /* color = */ color,
                )
            } else {
                context.blitSprite(
                    /* renderPipeline = */ RenderPipelines.GUI_TEXTURED,
                    /* location = */ PLAYER_MARK_WHITE_OUTLINE_TEXTURE,
                    /* x = */ markX,
                    /* y = */ y - 1,
                    /* width = */ 7,
                    /* height = */ 7,
                    /* color = */ color,
                )

                PlayerFaceExtractor.extractRenderState(
                    /* graphics = */ context,
                    /* texture = */ playerListEntry.skin.body.texturePath(),
                    /* x = */ markX + 1,
                    /* y = */ y,
                    /* size = */ 5,
                    /* hat = */ playerListEntry.showHat(),
                    /* flip = */ (playerMarker as? LivingEntity)
                        ?.let { LivingEntityRenderer.isUpsideDownName(it.name.string) }
                        ?: false,
                    /* color = */ -1
                )
            }

            if (config.showHeight) {
                val heightDiffNormalized = direction.normalize().y
                if (heightDiffNormalized > 0.5) { // about 45 deg
                    context.blitSprite(
                        /* renderPipeline = */ RenderPipelines.GUI_TEXTURED,
                        /* location = */ PLAYER_MARK_UP_TEXTURE,
                        /* x = */ markX + 1,
                        /* y = */ y - 5,
                        /* width = */ 5,
                        /* height = */ 4,
                    )
                } else if (heightDiffNormalized < -0.5) {
                    context.blitSprite(
                        /* renderPipeline = */ RenderPipelines.GUI_TEXTURED,
                        /* location = */ PLAYER_MARK_DOWN_TEXTURE,
                        /* x = */ markX + 1,
                        /* y = */ y + 7,
                        /* width = */ 5,
                        /* height = */ 4,
                    )
                }
            }
        }

        hudOffset.targetValue = if (isTabPressed && config.showNamesOnTab && namePlaques.isNotEmpty()) {
            HUD_OFFSET_TOTAL
        } else {
            0f
        }
        hudOffset.updateValues(client.frameTimeNs / 1000000f)

        val fadeProgress = round(hudOffset.currentValue / HUD_OFFSET_TOTAL * 255f) / 255f

        if (namePlaques.isNotEmpty() && fadeProgress > 0) {
            Profiler.get().push("plp-names")
            renderPlayerNamePlaques(context, namePlaques, y, fadeProgress)
            Profiler.get().pop()
        }

        relativePositionsLock.unlock()
        Profiler.get().pop()
    }

    private fun renderPlayerNamePlaques(
        context: GuiGraphicsExtractor,
        markers: List<NamePlaque>,
        barY: Int,
        fadeProgress: Float = 1f
    ) {
        val textRenderer = Minecraft.getInstance().font

        // sort markers by their distance from the center (closest first)
        val sortedMarkers = markers.sortedBy {
            abs(it.progress - 0.5)
        }

        // determine which markers should be visible
        val visibleMarkers = mutableListOf<Pair<NamePlaque, IntRange>>()

        for (marker in sortedMarkers) {
            val textWidth = textRenderer.width(marker.playerName)
            val plaqueWidth = textWidth + NAME_PLAQUE_PADDING_X * 2

            val plaqueX = marker.x - plaqueWidth / 2 + 4
            val plaqueXRange = plaqueX..(plaqueX + plaqueWidth)

            val overlap = visibleMarkers.any { (_, range) ->
                range.first - NAME_PLAQUE_OVERLAP_THRESHOLD <= plaqueXRange.last &&
                range.last + NAME_PLAQUE_OVERLAP_THRESHOLD >= plaqueXRange.first
            }

            if (!overlap) {
                visibleMarkers.add(marker to plaqueXRange)
            }
        }

        // render markers in visibleMarkers
        for ((marker, _) in visibleMarkers) {
            val textWidth = textRenderer.width(marker.playerName)
            val plaqueWidth = textWidth + NAME_PLAQUE_PADDING_X * 2
            val plaqueHeight = textRenderer.lineHeight + NAME_PLAQUE_PADDING_Y * 2

            val plaqueX = marker.x - plaqueWidth / 2 + 4
            val plaqueY = barY - plaqueHeight - NAME_PLAQUE_MARGIN

            val bgAlpha = (192 * fadeProgress).roundToInt()
            val textAlpha = (255 * fadeProgress).roundToInt()

            if (bgAlpha > 0) context.fill(
                plaqueX,
                plaqueY,
                plaqueX + plaqueWidth,
                plaqueY + plaqueHeight,
                (bgAlpha shl 24)
            )

            // for some reason, if the opacity is under 4, drawText just assumes the color does not include alpha
            if (textAlpha > 3) context.text(
                textRenderer,
                marker.playerName,
                plaqueX + NAME_PLAQUE_PADDING_X,
                plaqueY + NAME_PLAQUE_PADDING_Y,
                (textAlpha shl 24) or 0xFFFFFF,
                false
            )
        }
    }

    private fun getVanillaWaypoints(client: Minecraft): List<MarkerPosition> {
        if (!config.showVanillaWaypoints) return emptyList()

        val ret = mutableListOf<MarkerPosition>()
        client.connection?.waypointManager?.forEachWaypoint(client.cameraEntity!!) { waypoint ->
            val uuid = Either.unwrap(waypoint.id().mapRight {
                UUID.nameUUIDFromBytes("plp-waypoint:$it".toByteArray())
            })
            if (relativePositions.contains(uuid)) return@forEachWaypoint

            val tickManager = client.level!!.tickRateManager()
            val relativeYaw = waypoint.yawAngleToCamera(
                /* level = */ client.level!!,
                /* camera = */ client.gameRenderer.mainCamera(),
                /* partialTickSupplier = */ { ent ->
                    client.deltaTracker.getGameTimeDeltaPartialTick(!tickManager.isEntityFrozen(ent))
                }
            )
            val yaw = client.gameRenderer.mainCamera().yaw() + relativeYaw
            val directionVector = Vector3f(
                -Mth.sin(yaw * (Mth.PI / 180f)),
                0f,
                Mth.cos(yaw * (Mth.PI / 180f))
            ).normalize()

            var distance = waypoint.distanceSquared(client.cameraEntity!!)
            if (distance == Double.POSITIVE_INFINITY) {
                // vanilla thinks the distance is +infinity when the waypoint is >322 blocks away
                // 110224 = 332^2
                distance = 110224.0
            }

            ret.add(MarkerPosition(
                location = RelativePlayerLocation(
                    playerUuid = uuid,
                    direction = directionVector,
                    distance = sqrt(distance).toFloat(),
                    color = waypoint.icon().color.orElse(config.constantColor),
                ),
                projectionOrigin = null,
            ))
        }
        return ret
    }
}
