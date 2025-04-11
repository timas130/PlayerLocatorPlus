package sh.sit.plp.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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
        method = "renderExperienceLevel",
        at = @At(value = "HEAD")
    )
    private void beforeRenderExperienceLevel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        PlayerLocatorPlusClient.INSTANCE.render(context, tickCounter);

        float offset = PlayerLocatorPlusClient.INSTANCE.getCurrentHudOffset();
        if (offset > 0) {
            context.getMatrices().push();
            context.getMatrices().translate(0.0f, -offset, 0.0f);
        }
    }

    @Inject(
        method = "renderExperienceLevel",
        at = @At(value = "RETURN")
    )
    private void afterRenderExperienceLevel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (PlayerLocatorPlusClient.INSTANCE.getCurrentHudOffset() > 0) {
            context.getMatrices().pop();
        }
    }

    @Inject(
        method = "renderChat",
        at = @At(value = "HEAD")
    )
    private void beforeRenderChat(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        float offset = PlayerLocatorPlusClient.INSTANCE.getCurrentHudOffset();
        if (offset > 0) {
            context.getMatrices().push();
            context.getMatrices().translate(0.0f, -offset, 0.0f);
        }
    }

    @Inject(
        method = "renderChat",
        at = @At(value = "RETURN")
    )
    private void afterRenderChat(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (PlayerLocatorPlusClient.INSTANCE.getCurrentHudOffset() > 0) {
            context.getMatrices().pop();
        }
    }
}
