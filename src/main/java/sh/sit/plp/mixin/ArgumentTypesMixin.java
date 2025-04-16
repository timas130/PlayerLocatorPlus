package sh.sit.plp.mixin;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.command.argument.ArgumentTypes;
import net.minecraft.command.argument.serialize.ArgumentSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sh.sit.plp.color.ColorArgumentType;

@Mixin(ArgumentTypes.class)
public class ArgumentTypesMixin {
    @Inject(method = "getArgumentTypeProperties", at = @At("HEAD"), cancellable = true)
    private static <A extends ArgumentType<?>> void getArgumentTypeProperties(A argumentType, CallbackInfoReturnable<ArgumentSerializer.ArgumentTypeProperties<A>> cir) {
        if (argumentType instanceof ColorArgumentType) {
            // not unsafe, as argumentType (which is type A) is ColorArgumentType
            //noinspection unchecked
            cir.setReturnValue((ArgumentSerializer.ArgumentTypeProperties<A>)
                    ColorArgumentType.SERIALIZER.getArgumentTypeProperties(new ColorArgumentType()));
        }
    }
}
