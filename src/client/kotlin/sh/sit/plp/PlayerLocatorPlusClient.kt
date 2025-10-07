package sh.sit.plp

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.PlayerSkinDrawer
import net.minecraft.client.network.PlayerListEntry
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
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

object PlayerLocatorPlusClient : ClientModInitializer {
    private val EXPERIENCE_BAR_BACKGROUND_TEXTURE = Identifier.of(PlayerLocatorPlus.MOD_ID, "textures/gui/sprites/hud/empty_bar.png")!!
    private val PLAYER_MARK_TEXTURE = Identifier.of(PlayerLocatorPlus.MOD_ID, "textures/gui/sprites/hud/player_mark.png")!!
    private val PLAYER_MARK_UP_TEXTURE = Identifier.of(PlayerLocatorPlus.MOD_ID, "textures/gui/sprites/hud/player_mark_up.png")!!
    private val PLAYER_MARK_DOWN_TEXTURE = Identifier.of(PlayerLocatorPlus.MOD_ID, "textures/gui/sprites/hud/player_mark_down.png")!!
    private val PLAYER_MARK_WHITE_OUTLINE_TEXTURE = Identifier.of(PlayerLocatorPlus.MOD_ID, "textures/gui/sprites/hud/player_mark_white_outline.png")!!

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

        ClientPlayNetworking.registerGlobalReceiver(PlayerLocationsS2CPayload.TYPE) { payload, _, _ ->
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

    fun render(context: DrawContext, deltaTick: Float) {
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
            context.drawGuiTexture(
                texture = EXPERIENCE_BAR_BACKGROUND_TEXTURE,
                x = x,
                y = y,
                z = 0,
                width = barWidth,
                height = 5,
                color = 0xFFFFFFFF.toInt()
            )
        }

        relativePositionsLock.lock()

        val namePlaques = mutableListOf<NamePlaque>()

        val isTabPressed = client.options.playerListKey.isPressed

        for ((_, position) in relativePositions) {
            val playerMarker = player.world.getPlayerByUuid(position.playerUuid)
            val actualPosition = playerMarker
                ?.getLerpedPos(deltaTick)
            val direction = if (actualPosition != null) {
                actualPosition.subtract(player.getLerpedPos(deltaTick))
            } else if (position.distance == 0f) {
                Vec3d(position.direction)
            } else {
                val projectedPosition = lastUpdatePosition
                    .add(Vec3d(position.direction).multiply(position.distance.toDouble()))
                projectedPosition.subtract(player.getLerpedPos(deltaTick))
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
            if (progress !in 0.0..1.0) {
                continue
            }

            val markX = x + (progress * barWidth.toFloat()).roundToInt() - 4

            val showHeadIcon = config.alwaysShowHeads || (config.showHeadsOnTab && isTabPressed)

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
                    /* texture = */ playerListEntry.getSkinTextureExt(),
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
                        texture = PLAYER_MARK_UP_TEXTURE,
                        x = markX + 1,
                        y = y - 5,
                        z = 0,
                        width = 5,
                        height = 4,
                        color = 0xFFFFFFFF.toInt(),
                    )
                } else if (heightDiffNormalized < -0.5) {
                    context.drawGuiTexture(
                        texture = PLAYER_MARK_DOWN_TEXTURE,
                        x = markX + 1,
                        y = y + 7,
                        z = 0,
                        width = 5,
                        height = 4,
                        color = 0xFFFFFFFF.toInt(),
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
        drawTexturedQuad(
            texture,
            x,
            x + width,
            y,
            y + height,
            z,
            0f,
            1f,
            0f,
            1f,
            (color shr 16 and 0xFF) / 255f,
            (color shr 8 and 0xFF) / 255f,
            (color and 0xFF) / 255f,
            (color shr 24 and 0xFF) / 255.0f,
        )
    }

    private fun PlayerListEntry.getSkinTextureExt(): Identifier {
        // just so I don't have to make 2 separate versions for
        // playerListEntry.skinTexture and playerListEntry.skinTextures
        val resolver = FabricLoader.getInstance().mappingResolver

        val playerListEntryClass = resolver.unmapClassName("intermediary", PlayerListEntry::class.java.name)
        val skinTextureMethod = resolver.mapMethodName(
            "intermediary",
            playerListEntryClass,
            "method_2968", // getSkinTexture
            "()Lnet/minecraft/class_2960;" // Identifier
        )
        val skinTexturesMethod = resolver.mapMethodName(
            "intermediary",
            playerListEntryClass,
            "method_52810", // getSkinTextures
            "()Lnet/minecraft/class_8685;" // SkinTextures
        )

        val lookup = MethodHandles.publicLookup()
        try {
            val handle = lookup.findVirtual(
                PlayerListEntry::class.java,
                skinTextureMethod,
                MethodType.methodType(Identifier::class.java),
            )

            return handle.invoke(this) as Identifier
        } catch (_: NoSuchMethodException) {
        }

        val skinTexturesClass = Class.forName(
            // SkinTextures
            resolver.mapClassName("intermediary", "net.minecraft.class_8685"),
        )
        val handle = lookup.findVirtual(
            PlayerListEntry::class.java,
            skinTexturesMethod,
            MethodType.methodType(skinTexturesClass),
        )

        val skinTextures = handle.invoke(this)

        val textureField = resolver.mapMethodName(
            "intermediary",
            "net.minecraft.class_8685", // SkinTextures
            "comp_1626", // texture
            "()Lnet/minecraft/class_2960;" // Identifier
        )
        return lookup.findVirtual(
            skinTextures.javaClass,
            textureField,
            MethodType.methodType(Identifier::class.java)
        ).invoke(skinTextures) as Identifier
    }
}
