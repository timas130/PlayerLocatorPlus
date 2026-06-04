package sh.sit.plp.mixin;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sh.sit.plp.color.ColorArgumentType;

@Mixin(ArgumentTypeInfos.class)
public class ArgumentTypesMixin
{
    @Inject(method = "unpack", at = @At("HEAD"), cancellable = true)
    private static <A extends ArgumentType<?>> void getArgumentTypeProperties(A argumentType, CallbackInfoReturnable<ArgumentTypeInfo.Template<A>> cir)
    {
        if (argumentType instanceof ColorArgumentType)
        {
            cir.setReturnValue((ArgumentTypeInfo.Template<A>)
                    ColorArgumentType.SERIALIZER.unpack(new ColorArgumentType()));
        }
    }
}
