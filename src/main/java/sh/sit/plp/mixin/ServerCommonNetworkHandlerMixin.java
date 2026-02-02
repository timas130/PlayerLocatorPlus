package sh.sit.plp.mixin;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.WaypointS2CPacket;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sh.sit.plp.config.ConfigManager;

@Mixin(ServerCommonNetworkHandler.class)
public class ServerCommonNetworkHandlerMixin {
    @Inject(method = "sendPacket", at = @At(value = "HEAD"), cancellable = true)
    private void beforeSendPacket(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof WaypointS2CPacket && !ConfigManager.INSTANCE.getConfig().getAllowVanillaLocatorBar()) {
            ci.cancel();
        }
    }
}
