package sh.sit.plp.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sh.sit.plp.PlayerLocatorPlusClient;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Unique
    private float tickDelta = 0;

    @Inject(
        method = "render",
        at = @At(value = "HEAD")
    )
    private void beforeRender(DrawContext context, float tickDelta, CallbackInfo ci) {
        // I know this is technically a very bad idea, but how else
        // should I get the tickDelta from renderExperienceBar?
        this.tickDelta = tickDelta;
    }

    @Inject(
        method = "renderStatusBars",
        at = @At(value = "HEAD")
    )
    private void beforeRenderStatusBars(DrawContext context, CallbackInfo ci) {
        float offset = PlayerLocatorPlusClient.INSTANCE.getCurrentHudOffset();
        if (offset > 0) {
            context.getMatrices().push();
            context.getMatrices().translate(0.0f, -offset, 0.0f);
        }
    }

    @Inject(
        method = "renderStatusBars",
        at = @At(value = "RETURN")
    )
    private void afterRenderStatusBars(DrawContext context, CallbackInfo ci) {
        if (PlayerLocatorPlusClient.INSTANCE.getCurrentHudOffset() > 0) {
            context.getMatrices().pop();
        }
    }

    @Inject(
        method = "renderExperienceBar",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiler/Profiler;pop()V", ordinal = 0)
    )
    private void beforeRenderExperienceLevel(DrawContext context, int x, CallbackInfo ci) {
        PlayerLocatorPlusClient.INSTANCE.render(context, tickDelta);

        float offset = PlayerLocatorPlusClient.INSTANCE.getCurrentHudOffset();
        if (offset > 0) {
            context.getMatrices().push();
            context.getMatrices().translate(0.0f, -offset, 0.0f);
        }
    }

    @Inject(
        method = "renderExperienceBar",
        at = @At(value = "RETURN")
    )
    private void afterRenderExperienceLevel(DrawContext context, int x, CallbackInfo ci) {
        if (PlayerLocatorPlusClient.INSTANCE.getCurrentHudOffset() > 0) {
            context.getMatrices().pop();
        }
    }

    @Inject(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;render(Lnet/minecraft/client/gui/DrawContext;III)V")
    )
    private void beforeRenderChat(DrawContext context, float tickDelta, CallbackInfo ci) {
        float offset = PlayerLocatorPlusClient.INSTANCE.getCurrentHudOffset();
        if (offset > 0) {
            context.getMatrices().push();
            context.getMatrices().translate(0.0f, -offset, 0.0f);
        }
    }

    @Inject(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;render(Lnet/minecraft/client/gui/DrawContext;III)V",
                 shift = At.Shift.AFTER)
    )
    private void afterRenderChat(DrawContext context, float tickDelta, CallbackInfo ci) {
        if (PlayerLocatorPlusClient.INSTANCE.getCurrentHudOffset() > 0) {
            context.getMatrices().pop();
        }
    }
}
