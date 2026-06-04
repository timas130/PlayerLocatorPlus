package sh.sit.plp.color

import com.mojang.authlib.GameProfile
import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.GameProfileArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.permissions.Permissions
import net.minecraft.server.players.NameAndId
import sh.sit.plp.BarUpdater
import sh.sit.plp.PlayerLocatorPlus
import sh.sit.plp.config.ConfigManager
import sh.sit.plp.config.ModConfig


object PLPCommand {
    private val WRONG_COLOR_MODE =
        SimpleCommandExceptionType(Component.translatable("commands.player-locator-plus.color.wrong-color-mode"))
    private val NON_SINGLE_PLAYER =
        SimpleCommandExceptionType(Component.translatable("commands.player-locator-plus.color.non-single-player"))

    fun register() {
        CommandRegistrationCallback.EVENT.register(CommandRegistrationCallback { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("plp")
                    .then(
                        Commands.literal("reload")
                            .executes { c ->
                                c.source.sendSuccess({ Component.literal("Player Locator config reloaded") }, false)
                                ConfigManager.reload(fromDisk = true)
                                BarUpdater.fullResend(c.source.server)
                                Command.SINGLE_SUCCESS
                            })
                    .then(
                        Commands.literal("random")
                            .requires { it.isPlayer && it.permissions().hasPermission(Permissions.COMMANDS_ADMIN) }
                            .executes { c ->
                                c.source.player?.let { BarUpdater.sendFakePlayers(it) }
                                Command.SINGLE_SUCCESS
                            })
                    .then(
                        Commands.literal("color")
                            .then(
                                Commands.argument("color", ColorArgumentType())
                                    .requires { it.isPlayer }
                                    .suggests { _, builder ->
                                        // Fix for a weird bug on Forge (+Sinytra Connector).
                                        // It only includes the custom id in CommandTreeS2CPacket if customSuggestions != null,
                                        // whereas Fabric includes it if the id itself is not null.
                                        // See also: https://minecraft.wiki/w/Java_Edition_protocol/Command_data#Node_Format
                                        builder.buildFuture()
                                    }
                                    .executes { c ->
                                        runChangeColor(c, true)
                                    }
                                    .then(
                                        Commands.argument("player", GameProfileArgument.gameProfile())
                                            .requires { it.permissions().hasPermission(Permissions.COMMANDS_MODERATOR) }
                                            .suggests { conComponent, builder ->
                                                SharedSuggestionProvider.suggest(
                                                    conComponent.source.server.playerList.players.map { it.gameProfile.name },
                                                    builder
                                                )
                                            }
                                            .executes { c ->
                                                runChangeColor(c, false)
                                            })
                            )
                    )
            )
        })
    }

    private fun NameAndId.toGameProfile(): GameProfile {
        return GameProfile(id, name)
    }

    private fun runChangeColor(c: CommandContext<CommandSourceStack>, self: Boolean): Int {
        if (PlayerLocatorPlus.config.colorMode != ModConfig.ColorMode.CUSTOM) {
            throw WRONG_COLOR_MODE.create()
        }

        val player = if (self) {
            c.source.playerOrException.gameProfile
        } else {
            val players = GameProfileArgument.getGameProfiles(c, "player")
            players.singleOrNull()?.toGameProfile() ?: throw NON_SINGLE_PLAYER.create()
        }

        val color = c.getArgument("color", Int::class.java)

        PlayerDataState.of(c.source.server).run {
            this!!.getPlayer(player.id).customColor = color
            setDirty()
        }
        c.source.sendSuccess(
            if (self) {
                {
                    Component.translatable(
                        "commands.player-locator-plus.color.self",
                        formatColor(color)
                    )
                }
            } else {
                {
                    Component.translatable(
                        "commands.player-locator-plus.color.other",
                        Component.literal(player.name),
                        formatColor(color)
                    )
                }
            },
            false
        )

        return Command.SINGLE_SUCCESS
    }

    private fun formatColor(color: Int): Component {
        val colorHex = "#" + color.toString(16).padStart(6, '0')
        return Component.literal(colorHex).withColor(color)
    }
}
