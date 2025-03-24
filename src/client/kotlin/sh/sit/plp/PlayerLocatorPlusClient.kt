package sh.sit.plp

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import net.minecraft.world.GameMode
import org.joml.Vector2d
import sh.sit.plp.PlayerLocatorPlus.config
import sh.sit.plp.network.PlayerLocationsS2CPayload
import sh.sit.plp.network.RelativePlayerLocation
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.roundToInt

object PlayerLocatorPlusClient : ClientModInitializer {
    private val EXPERIENCE_BAR_BACKGROUND_TEXTURE = Identifier.of(PlayerLocatorPlus.MOD_ID, "hud/empty_bar")
    private val PLAYER_MARK_TEXTURE = Identifier.of(PlayerLocatorPlus.MOD_ID, "hud/player_mark")
    private val PLAYER_MARK_UP_TEXTURE = Identifier.of(PlayerLocatorPlus.MOD_ID, "hud/player_mark_up")
    private val PLAYER_MARK_DOWN_TEXTURE = Identifier.of(PlayerLocatorPlus.MOD_ID, "hud/player_mark_down")

    private val relativePositionsLock = ReentrantLock()
    private var lastUpdatePosition = Vec3d.ZERO
    private val relativePositions = mutableMapOf<UUID, RelativePlayerLocation>()

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

        HudRenderCallback.EVENT.register(HudRenderCallback(::render))
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
            networkHandler?.playerList?.any { it.profile.id != player.uuid } != true &&
            relativePositions.isEmpty()
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

        for ((_, position) in relativePositions) {
            val actualPosition = player.world.getPlayerByUuid(position.playerUuid)
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
            val relativeAngle = -direction2d.angle(Vector2d(rotationVec.x, rotationVec.z)) * 180.0 / Math.PI

            val horizontalFov = Utils.calculateHorizontalFov(
                verticalFov = client.options.fov.value,
                width = context.scaledWindowWidth,
                height = context.scaledWindowHeight
            )
            val progress = (relativeAngle + horizontalFov / 2) / horizontalFov
            if (progress < 0 || progress > 1) {
                continue
            }

            val opacity = if (config.fadeMarkers) {
                val dist = position.distance.coerceIn(config.fadeStart.toFloat(), config.fadeEnd.toFloat())
                val fadeProgress = 1 - (dist - config.fadeStart) / (config.fadeEnd - config.fadeStart)
                (((1 - config.fadeEndOpacity) * fadeProgress + config.fadeEndOpacity) * 255).roundToInt()
            } else {
                255
            }
            val color = (opacity shl 24) or Utils.uuidToColor(position.playerUuid)

            val markX = x + (progress * barWidth.toFloat()).roundToInt() - 4
            context.drawGuiTexture(
                texture = PLAYER_MARK_TEXTURE,
                x = markX,
                y = y - 1,
                z = 0,
                width = 7,
                height = 7,
                color = color,
            )

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

        relativePositionsLock.unlock()
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
