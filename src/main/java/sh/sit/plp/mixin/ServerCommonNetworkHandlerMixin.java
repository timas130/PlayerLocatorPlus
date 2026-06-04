package sh.sit.plp.mixin;


import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sh.sit.plp.config.ConfigManager;

@Mixin(ServerCommonPacketListenerImpl.class)
public class ServerCommonNetworkHandlerMixin
{
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At(value = "HEAD"), cancellable = true)
    private void beforeSendPacket(Packet<?> packet, CallbackInfo ci)
    {
        if (packet instanceof ClientboundTrackedWaypointPacket && !ConfigManager.INSTANCE.getConfig().getAllowVanillaLocatorBar())
        {
            ci.cancel();
        }
    }
}
