package sh.sit.plp.mixin;

import net.minecraft.commands.arguments.ColorArgument;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sh.sit.plp.PlayerLocatorPlus;
import sh.sit.plp.color.ColorArgumentType;

@Mixin(targets = "net.minecraft.network.protocol.game.ClientboundCommandsPacket$ArgumentNodeStub")
public class CommandTreeS2CPacketArgumentNodeMixin
{


    @Mutable
    @Shadow
    @Final
    private ArgumentTypeInfo.Template<?> argumentType;

    @Mutable
    @Shadow
    @Final
    private @Nullable Identifier suggestionId;

    @Inject(method = "<init>(Ljava/lang/String;Lnet/minecraft/commands/synchronization/ArgumentTypeInfo$Template;Lnet/minecraft/resources/Identifier;)V", at = @At("TAIL"))
    private void afterConstructor(String id, ArgumentTypeInfo.Template argumentType, Identifier suggestionId, CallbackInfo ci)
    {
        if (id != null && id.equals(Identifier.fromNamespaceAndPath(PlayerLocatorPlus.MOD_ID, "color")))
        {
            this.suggestionId = null;
            this.argumentType = SingletonArgumentInfo.contextFree(ColorArgumentType::new).unpack(new ColorArgumentType());
        }
    }

    @Inject(method = "write", at = @At("HEAD"))
    void beforeWrite(FriendlyByteBuf output, CallbackInfo ci)
    {
        if (this.argumentType.type() == ColorArgumentType.SERIALIZER)
        {
            this.suggestionId = Identifier.fromNamespaceAndPath(PlayerLocatorPlus.MOD_ID, "color");
            this.argumentType = ArgumentTypeInfos.byClass(ColorArgument.color()).unpack(ColorArgument.color());
        }
    }
}
