package sh.sit.plp.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sh.sit.plp.PlayerLocatorPlusClient;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(
        method = "renderStatusBars",
        at = @At(value = "HEAD")
    )
    private void beforeRenderStatusBars(DrawContext context, CallbackInfo ci) {
        float offset = PlayerLocatorPlusClient.INSTANCE.getCurrentHudOffset();
        if (offset > 0) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(0.0f, -offset);
        }
    }

    @Inject(
        method = "renderStatusBars",
        at = @At(value = "RETURN")
    )
    private void afterRenderStatusBars(DrawContext context, CallbackInfo ci) {
        if (PlayerLocatorPlusClient.INSTANCE.getCurrentHudOffset() > 0) {
            context.getMatrices().popMatrix();
        }
    }

    @Inject(
        method = "renderMainHud",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;hasExperienceBar()Z")
    )
    private void beforeRenderExperienceLevel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        PlayerLocatorPlusClient.INSTANCE.render(context, tickCounter);

        float offset = PlayerLocatorPlusClient.INSTANCE.getCurrentHudOffset();
        if (offset > 0) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(0.0f, -offset);
        }
    }

    @Inject(
        method = "renderMainHud",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/bar/Bar;renderAddons(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V")
    )
    private void afterRenderExperienceLevel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (PlayerLocatorPlusClient.INSTANCE.getCurrentHudOffset() > 0) {
            context.getMatrices().popMatrix();
        }
    }

    @Inject(
        method = "renderChat",
        at = @At(value = "HEAD")
    )
    private void beforeRenderChat(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        float offset = PlayerLocatorPlusClient.INSTANCE.getCurrentHudOffset();
        if (offset > 0) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(0.0f, -offset);
        }
    }

    @Inject(
        method = "renderChat",
        at = @At(value = "RETURN")
    )
    private void afterRenderChat(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (PlayerLocatorPlusClient.INSTANCE.getCurrentHudOffset() > 0) {
            context.getMatrices().popMatrix();
        }
    }

    @Inject(
        method = "getCurrentBarType",
        at = @At(value = "RETURN"),
        cancellable = true
    )
    private void getCurrentBarType(CallbackInfoReturnable<InGameHud.BarType> cir) {
        if (cir.getReturnValue() == InGameHud.BarType.LOCATOR && PlayerLocatorPlusClient.INSTANCE.isBarVisible()) {
            cir.setReturnValue(InGameHud.BarType.EXPERIENCE);
        }
    }
}
