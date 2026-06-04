package sh.sit.plp.color

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.ChatFormatting
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.ColorArgument
import net.minecraft.commands.synchronization.SingletonArgumentInfo
import java.util.concurrent.CompletableFuture

class ColorArgumentType : ArgumentType<Int> {
    companion object {
        @JvmField
        val SERIALIZER = SingletonArgumentInfo.contextFree(::ColorArgumentType)

        @JvmField
        val suggestionProvider = SuggestionProvider<SharedSuggestionProvider> { commandContext, suggestionsBuilder ->
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
        val formatting = ChatFormatting.getByName(string)

        return if (formatting != null && formatting.isColor) {
            formatting.color!!
        } else if (string.startsWith('#') && string.length == 7) {
            try {
                string.substring(1).toInt(16)
            } catch (_: NumberFormatException) {
                throw ColorArgument.ERROR_INVALID_VALUE.createWithContext(reader, string)
            }
        } else {
            throw ColorArgument.ERROR_INVALID_VALUE.createWithContext(reader, string)
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

        val colorNames = ChatFormatting.getNames(true, false)
        return SharedSuggestionProvider.suggest(colorNames, builder)
    }
}
