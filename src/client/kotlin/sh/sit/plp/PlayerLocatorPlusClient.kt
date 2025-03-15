package sh.sit.plp

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import org.joml.Vector2d
import sh.sit.plp.network.PlayerLocationsS2CPayload
import sh.sit.plp.network.RelativePlayerLocation
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.roundToInt

object PlayerLocatorPlusClient : ClientModInitializer {
    private val PLAYER_LOCATOR_LAYER = Identifier.of(PlayerLocatorPlus.MOD_ID, "player_locator")

    private val EXPERIENCE_BAR_BACKGROUND_TEXTURE = Identifier.of(PlayerLocatorPlus.MOD_ID, "hud/empty_bar")
    private val PLAYER_MARK_TEXTURE = Identifier.of(PlayerLocatorPlus.MOD_ID, "hud/player_mark")
    private val PLAYER_MARK_UP_TEXTURE = Identifier.of(PlayerLocatorPlus.MOD_ID, "hud/player_mark_up")
    private val PLAYER_MARK_DOWN_TEXTURE = Identifier.of(PlayerLocatorPlus.MOD_ID, "hud/player_mark_down")

    private val relativePositionsLock = ReentrantLock()
    private var lastUpdatePosition = Vec3d.ZERO
    private val relativePositions = mutableMapOf<UUID, RelativePlayerLocation>()

    override fun onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(PlayerLocationsS2CPayload.ID) { payload, ctx ->
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

        HudLayerRegistrationCallback.EVENT.register(HudLayerRegistrationCallback { drawer ->
            drawer.attachLayerAfter(IdentifiedLayer.EXPERIENCE_LEVEL, PLAYER_LOCATOR_LAYER, ::render)
        })
    }

    fun render(context: DrawContext, tickCounter: RenderTickCounter) {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        val interactionManager = client.interactionManager ?: return

        val barWidth = 182
        val x = context.scaledWindowWidth / 2 - 91
        val y = context.scaledWindowHeight - 32 + 3

        val barRendered = player.jumpingMount != null || interactionManager.hasExperienceBar()
        if (!barRendered) {
            context.drawGuiTexture(RenderLayer::getGuiTextured, EXPERIENCE_BAR_BACKGROUND_TEXTURE, x, y, barWidth, 5)
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

            val opacity = if (PlayerLocatorPlus.config.fadeMarkers) {
                (((700.0 - position.distance) / 700.0).coerceIn(0.3, 1.0) * 255).roundToInt()
            } else {
                255
            }
            val color = (opacity shl 24) or Utils.uuidToColor(position.playerUuid)

            val markX = x + (progress * barWidth.toFloat()).roundToInt() - 4
            context.drawGuiTexture(
                RenderLayer::getGuiTextured,
                PLAYER_MARK_TEXTURE,
                markX,
                y - 1,
                7,
                7,
                color,
            )

            if (PlayerLocatorPlus.config.showHeight) {
                val heightDiffNormalized = direction.normalize().y
                if (heightDiffNormalized > 0.5) { // about 45 deg
                    context.drawGuiTexture(
                        RenderLayer::getGuiTextured,
                        PLAYER_MARK_UP_TEXTURE,
                        markX + 1,
                        y - 5,
                        5,
                        4,
                    )
                } else if (heightDiffNormalized < -0.5) {
                    context.drawGuiTexture(
                        RenderLayer::getGuiTextured,
                        PLAYER_MARK_DOWN_TEXTURE,
                        markX + 1,
                        y + 7,
                        5,
                        4,
                    )
                }
            }
        }

        relativePositionsLock.unlock()
    }
}
