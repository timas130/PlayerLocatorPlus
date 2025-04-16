package sh.sit.plp.color

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.command.CommandSource
import net.minecraft.command.argument.serialize.ConstantArgumentSerializer
import net.minecraft.util.Formatting
import java.util.concurrent.CompletableFuture

class ColorArgumentType : ArgumentType<Int> {
    companion object {
        @JvmField
        val SERIALIZER = ConstantArgumentSerializer.of(::ColorArgumentType)!!

        @JvmField
        val suggestionProvider = SuggestionProvider<CommandSource> { commandContext, suggestionsBuilder ->
            ColorArgumentType().listSuggestions(commandContext, suggestionsBuilder)
        }
    }

    override fun parse(reader: StringReader): Int {
        val string = if (reader.peek() == '#') {
            reader.skip()
            "#" + reader.readUnquotedString()
        } else {
            reader.readUnquotedString()
        }
        val formatting = Formatting.byName(string)

        return if (formatting != null && formatting.isColor) {
            formatting.colorValue!!
        } else if (string.startsWith('#') && string.length == 7) {
            try {
                string.substring(1).toInt(16)
            } catch (e: NumberFormatException) {
                throw net.minecraft.command.argument.ColorArgumentType
                    .INVALID_COLOR_EXCEPTION.createWithContext(reader, string)
            }
        } else {
            throw net.minecraft.command.argument.ColorArgumentType
                .INVALID_COLOR_EXCEPTION.createWithContext(reader, string)
        }
    }

    override fun <S> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        if (builder.remaining.isBlank()) {
            builder.suggest("#")
        }
        if (builder.remaining.startsWith("#") && builder.remaining.length < 7) {
            for (c in "0123456789abcdef") {
                builder.suggest(builder.remaining + c.toString())
            }
        }

        val colorNames = Formatting.getNames(true, false)
        return CommandSource.suggestMatching(colorNames, builder)
    }
}
