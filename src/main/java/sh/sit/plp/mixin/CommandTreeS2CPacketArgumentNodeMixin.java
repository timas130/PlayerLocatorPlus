package sh.sit.plp.mixin;

import net.minecraft.command.argument.ArgumentTypes;
import net.minecraft.command.argument.serialize.ArgumentSerializer;
import net.minecraft.command.argument.serialize.ConstantArgumentSerializer;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sh.sit.plp.PlayerLocatorPlus;
import sh.sit.plp.color.ColorArgumentType;

@Mixin(targets = "net.minecraft.network.packet.s2c.play.CommandTreeS2CPacket$ArgumentNode")
public class CommandTreeS2CPacketArgumentNodeMixin {
    @Mutable @Shadow @Final @Nullable private Identifier id;

    @Mutable @Shadow @Final private ArgumentSerializer.ArgumentTypeProperties<?> properties;

    @Inject(method = "<init>(Ljava/lang/String;Lnet/minecraft/command/argument/serialize/ArgumentSerializer$ArgumentTypeProperties;Lnet/minecraft/util/Identifier;)V", at = @At("TAIL"))
    private void afterConstructor(String name, ArgumentSerializer.ArgumentTypeProperties<?> properties, Identifier id, CallbackInfo ci) {
        if (id != null && id.equals(Identifier.of(PlayerLocatorPlus.MOD_ID, "color"))) {
            this.id = null;
            this.properties = ConstantArgumentSerializer.of(ColorArgumentType::new)
                    .getArgumentTypeProperties(new ColorArgumentType());
        }
    }

    @Inject(method = "write(Lnet/minecraft/network/PacketByteBuf;)V", at = @At("HEAD"))
    void beforeWrite(PacketByteBuf buf, CallbackInfo ci) {
        if (this.properties.getSerializer() == ColorArgumentType.SERIALIZER) {
            this.id = Identifier.of(PlayerLocatorPlus.MOD_ID, "color");
            this.properties = ArgumentTypes.get(net.minecraft.command.argument.ColorArgumentType.color())
                    .getArgumentTypeProperties(net.minecraft.command.argument.ColorArgumentType.color());
        }
    }
}
