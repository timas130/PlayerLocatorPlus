package sh.sit.plp.mixin;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sh.sit.plp.color.ColorArgumentType;

@Mixin(value = ArgumentCommandNode.class, remap = false)
public class ArgumentCommandNodeMixin<S, T> {
    @Shadow @Final private ArgumentType<T> type;

    @Inject(method = "getCustomSuggestions", at = @At("HEAD"), cancellable = true)
    void getCustomSuggestions(CallbackInfoReturnable<SuggestionProvider<S>> cir) {
        if (this.type instanceof ColorArgumentType) {
            //noinspection unchecked
            cir.setReturnValue((SuggestionProvider<S>) ColorArgumentType.suggestionProvider);
        }
    }
}
