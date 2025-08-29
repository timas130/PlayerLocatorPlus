package sh.sit.plp.color

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.command.CommandSource
import net.minecraft.command.argument.GameProfileArgumentType
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import sh.sit.plp.BarUpdater
import sh.sit.plp.PlayerLocatorPlus
import sh.sit.plp.config.ConfigManager
import sh.sit.plp.config.ModConfig

object PLPCommand {
    private val WRONG_COLOR_MODE = SimpleCommandExceptionType(Text.translatable("commands.player-locator-plus.color.wrong-color-mode"))
    private val NON_SINGLE_PLAYER = SimpleCommandExceptionType(Text.translatable("commands.player-locator-plus.color.non-single-player"))

    fun register() {
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
                    .requires { it.isExecutedByPlayer && it.hasPermissionLevel(2) }
                    .executes { c ->
                        c.source.player?.let { BarUpdater.sendFakePlayers(it) }
                        Command.SINGLE_SUCCESS
                    })
                .then(CommandManager.literal("color")
                    .then(CommandManager.argument("color", ColorArgumentType())
                        .requires { it.isExecutedByPlayer }
                        .executes { c ->
                            runChangeColor(c, true)
                        }
                        .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                            .requires { it.hasPermissionLevel(2) }
                            .suggests { context, builder ->
                                CommandSource.suggestMatching(
                                    context.source.server.playerManager.playerList.map { it.gameProfile.name },
                                    builder
                                )
                            }
                            .executes { c ->
                                runChangeColor(c, false)
                            }))))
        })
    }

    private fun runChangeColor(c: CommandContext<ServerCommandSource>, self: Boolean): Int {
        if (PlayerLocatorPlus.config.colorMode != ModConfig.ColorMode.CUSTOM) {
            throw WRONG_COLOR_MODE.create()
        }

        val player = if (self) {
            c.source.playerOrThrow.gameProfile
        } else {
            val players = GameProfileArgumentType.getProfileArgument(c, "player")
            players.singleOrNull() ?: throw NON_SINGLE_PLAYER.create()
        }

        val color = c.getArgument("color", Int::class.java)

        PlayerDataState.of(c.source.server).run {
            getPlayer(player.id).customColor = color
            markDirty()
        }
        c.source.sendFeedback(
            if (self) {
                { Text.translatable(
                    "commands.player-locator-plus.color.self",
                    formatColor(color)
                ) }
            } else {
                { Text.translatable(
                    "commands.player-locator-plus.color.other",
                    Text.of(player.name),
                    formatColor(color)
                ) }
            },
            false
        )

        return Command.SINGLE_SUCCESS
    }

    private fun formatColor(color: Int): Text {
        val colorHex = "#" + color.toString(16).padStart(6, '0')
        return Text.literal(colorHex).styled {
            it.withColor(color)
        }
    }
}
