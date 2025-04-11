package sh.sit.plp

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.PlayerSkinDrawer
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.client.render.entity.LivingEntityRenderer
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import net.minecraft.world.GameMode
import org.joml.Vector2d
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

object PlayerLocatorPlusClient : ClientModInitializer {
    private val EXPERIENCE_BAR_BACKGROUND_TEXTURE = Identifier.of(PlayerLocatorPlus.MOD_ID, "hud/empty_bar")
    private val PLAYER_MARK_TEXTURE = Identifier.of(PlayerLocatorPlus.MOD_ID, "hud/player_mark")
    private val PLAYER_MARK_UP_TEXTURE = Identifier.of(PlayerLocatorPlus.MOD_ID, "hud/player_mark_up")
    private val PLAYER_MARK_DOWN_TEXTURE = Identifier.of(PlayerLocatorPlus.MOD_ID, "hud/player_mark_down")
    private val PLAYER_MARK_WHITE_OUTLINE_TEXTURE = Identifier.of(PlayerLocatorPlus.MOD_ID, "hud/player_mark_white_outline")

    private const val NAME_PLAQUE_PADDING_X = 4
    private const val NAME_PLAQUE_PADDING_Y = 2
    private const val NAME_PLAQUE_MARGIN = 2
    private const val NAME_PLAQUE_OVERLAP_THRESHOLD = 2

    private const val HUD_OFFSET_TOTAL = 16f
    private var hudOffset = Animatable(0f)

    // for mixin
    val currentHudOffset get() = hudOffset.currentValue

    private val relativePositionsLock = ReentrantLock()
    private var lastUpdatePosition = Vec3d.ZERO
    private val relativePositions = mutableMapOf<UUID, RelativePlayerLocation>()

    private data class NamePlaque(
        val x: Int,
        val playerName: String,
        val progress: Double
    )

    override fun onInitializeClient() {
        ConfigManagerClient.init()

        ClientPlayNetworking.registerGlobalReceiver(PlayerLocationsS2CPayload.ID) { payload, _ ->
            relativePositionsLock.lock()
            if (payload.fullReset) {
                relativePositions.clear()
            } else {
                payload.removeUuids.forEach {
                    relativePositions.remove(it)
                }
            }

            for (update in payload.locationUpdates) {
                relativePositions.compute(update.playerUuid) { _, _ ->
                    update
                }
            }

            lastUpdatePosition = MinecraftClient.getInstance().player?.pos ?: Vec3d.ZERO
            relativePositionsLock.unlock()
        }
    }

    private fun isBarVisible(client: MinecraftClient): Boolean {
        val player = client.player ?: return false
        val interactionManager = client.interactionManager ?: return false
        val inGameHud = client.inGameHud
        val networkHandler = client.networkHandler

        // hide when disabled
        if (!config.visible) {
            return false
        }
        // hide in F1
        if (client.options.hudHidden) {
            return false
        }
        // hide when there are no other players online and relativePositions is empty
        if (
            !config.visibleEmpty &&
            relativePositions.isEmpty() &&
            networkHandler?.playerList?.any { it.profile.id != player.uuid } != true
        ) {
            return false
        }
        // hide in spectator mode when the spectator menu is not open
        if (interactionManager.currentGameMode == GameMode.SPECTATOR && !inGameHud.spectatorHud.isOpen) {
            return false
        }

        return true
    }

    fun render(context: DrawContext, tickCounter: RenderTickCounter) {
        if (!config.visible) return

        val client = MinecraftClient.getInstance()
        if (!isBarVisible(client)) return

        client.profiler.push("plp")
        val player = client.player ?: return
        val interactionManager = client.interactionManager ?: return

        val barWidth = 182
        val x = context.scaledWindowWidth / 2 - 91
        val y = context.scaledWindowHeight - 32 + 3

        val barRendered = player.jumpingMount != null || interactionManager.hasExperienceBar()
        if (!barRendered) {
            context.drawGuiTexture(EXPERIENCE_BAR_BACKGROUND_TEXTURE, x, y, barWidth, 5)
        }

        relativePositionsLock.lock()

        val namePlaques = mutableListOf<NamePlaque>()

        val isTabPressed = client.options.playerListKey.isPressed

        for ((_, position) in relativePositions) {
            val playerMarker = player.world.getPlayerByUuid(position.playerUuid)
            val actualPosition = playerMarker
                ?.getLerpedPos(tickCounter.getTickDelta(false))
            val direction = if (actualPosition != null) {
                actualPosition.subtract(player.getLerpedPos(tickCounter.getTickDelta(false)))
            } else if (position.distance == 0f) {
                Vec3d(position.direction)
            } else {
                val projectedPosition = lastUpdatePosition
                    .add(Vec3d(position.direction).multiply(position.distance.toDouble()))
                projectedPosition.subtract(player.getLerpedPos(tickCounter.getTickDelta(false)))
            }

            val direction2d = Vector2d(direction.x, direction.z)
            if (!direction2d.isFinite) {
                continue
            }
            val rotationVec = player.getRotationVec(1f)
            var relativeAngle = -direction2d.angle(Vector2d(rotationVec.x, rotationVec.z)) * 180.0 / Math.PI
            if (relativeAngle.isNaN()) {
                relativeAngle = 0.0
            }

            val horizontalFov = MathUtils.calculateHorizontalFov(
                verticalFov = client.options.fov.value,
                width = context.scaledWindowWidth,
                height = context.scaledWindowHeight
            )
            val progress = (relativeAngle + horizontalFov / 2) / horizontalFov
            if (progress < 0 || progress > 1) {
                continue
            }

            val markX = x + (progress * barWidth.toFloat()).roundToInt() - 4

            val showHeadIcon = config.showHeadsOnTab && isTabPressed

            val playerList = client.networkHandler?.playerList ?: emptyList()
            val playerListEntry = playerList.find { it.profile.id == position.playerUuid }

            val opacity = if (config.fadeMarkers) {
                val dist = position.distance.coerceIn(config.fadeStart.toFloat(), config.fadeEnd.toFloat())
                val fadeProgress = 1 - (dist - config.fadeStart) / (config.fadeEnd - config.fadeStart)
                (((1 - config.fadeEndOpacity) * fadeProgress + config.fadeEndOpacity) * 255).roundToInt()
            } else {
                255
            }
            val color = (opacity shl 24) or position.color

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
                context.drawGuiTexture(
                    texture = PLAYER_MARK_TEXTURE,
                    x = markX,
                    y = y - 1,
                    z = 0,
                    width = 7,
                    height = 7,
                    color = color,
                )
            } else {
                context.drawGuiTexture(
                    texture = PLAYER_MARK_WHITE_OUTLINE_TEXTURE,
                    x = markX,
                    y = y - 1,
                    z = 0,
                    width = 7,
                    height = 7,
                    color = color,
                )

                PlayerSkinDrawer.draw(
                    /* context = */ context,
                    /* texture = */ playerListEntry.skinTextures.texture,
                    /* x = */ markX + 1,
                    /* y = */ y,
                    /* size = */ 5,
                    /* hatVisible = */ false,
                    /* upsideDown = */ playerMarker?.let { LivingEntityRenderer.shouldFlipUpsideDown(it) } ?: false,
                )
            }

            if (config.showHeight) {
                val heightDiffNormalized = direction.normalize().y
                if (heightDiffNormalized > 0.5) { // about 45 deg
                    context.drawGuiTexture(
                        /* texture = */ PLAYER_MARK_UP_TEXTURE,
                        /* x = */ markX + 1,
                        /* y = */ y - 5,
                        /* width = */ 5,
                        /* height = */ 4,
                    )
                } else if (heightDiffNormalized < -0.5) {
                    context.drawGuiTexture(
                        /* texture = */ PLAYER_MARK_DOWN_TEXTURE,
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
        hudOffset.updateValues(client.renderTime / 1000000f)

        val fadeProgress = round(hudOffset.currentValue / HUD_OFFSET_TOTAL * 255f) / 255f

        if (namePlaques.isNotEmpty() && fadeProgress > 0) {
            client.profiler.push("plp-names")
            renderPlayerNamePlaques(context, namePlaques, y, fadeProgress)
            client.profiler.pop()
        }

        relativePositionsLock.unlock()
        client.profiler.pop()
    }

    private fun renderPlayerNamePlaques(
        context: DrawContext,
        markers: List<NamePlaque>,
        barY: Int,
        fadeProgress: Float = 1f
    ) {
        val textRenderer = MinecraftClient.getInstance().textRenderer

        // sort markers by their distance from the center (closest first)
        val sortedMarkers = markers.sortedBy {
            abs(it.progress - 0.5)
        }

        // determine which markers should be visible
        val visibleMarkers = mutableListOf<Pair<NamePlaque, IntRange>>()

        for (marker in sortedMarkers) {
            val textWidth = textRenderer.getWidth(marker.playerName)
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
            val textWidth = textRenderer.getWidth(marker.playerName)
            val plaqueWidth = textWidth + NAME_PLAQUE_PADDING_X * 2
            val plaqueHeight = textRenderer.fontHeight + NAME_PLAQUE_PADDING_Y * 2

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
            if (textAlpha > 3) context.drawText(
                textRenderer,
                marker.playerName,
                plaqueX + NAME_PLAQUE_PADDING_X,
                plaqueY + NAME_PLAQUE_PADDING_Y,
                (textAlpha shl 24) or 0xFFFFFF,
                false
            )
        }
    }

    private fun DrawContext.drawGuiTexture(texture: Identifier, x: Int, y: Int, z: Int, width: Int, height: Int, color: Int) {
        val sprite = guiAtlasManager.getSprite(texture)
        drawTexturedQuad(
            sprite.atlasId,
            x,
            x + width,
            y,
            y + height,
            z,
            sprite.minU,
            sprite.maxU,
            sprite.minV,
            sprite.maxV,
            (color shr 16 and 0xFF) / 255f,
            (color shr 8 and 0xFF) / 255f,
            (color and 0xFF) / 255f,
            (color shr 24 and 0xFF) / 255.0f,
        )
    }
}
