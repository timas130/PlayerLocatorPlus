package sh.sit.plp.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.spectator.SpectatorGui;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sh.sit.plp.PlayerLocatorPlusClient;

import java.util.Objects;

@Mixin(Gui.class)
public class InGameHudMixin
{

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private SpectatorGui spectatorGui;

    @Inject(
            method = "extractPlayerHealth",
            at = @At(value = "HEAD")
    )
    private void beforeRenderStatusBars(GuiGraphicsExtractor graphics, CallbackInfo ci)
    {
        float offset = PlayerLocatorPlusClient.INSTANCE.getCurrentHudOffset();
        if (offset > 0)
        {
            graphics.pose().pushMatrix();
            graphics.pose().translate(0.0f, -offset);
        }
    }

    @Inject(
            method = "extractPlayerHealth",
            at = @At(value = "RETURN")
    )
    private void afterRenderStatusBars(GuiGraphicsExtractor graphics, CallbackInfo ci)
    {
        if (PlayerLocatorPlusClient.INSTANCE.getCurrentHudOffset() > 0)
        {
            graphics.pose().popMatrix();
        }
    }

    @Inject(
            method = "extractHotbarAndDecorations",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;hasExperience()Z")
    )
    private void beforeRenderExperienceLevel(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci)
    {
        PlayerLocatorPlusClient.INSTANCE.render(graphics, deltaTracker);

        float offset = PlayerLocatorPlusClient.INSTANCE.getCurrentHudOffset();
        if (offset > 0)
        {
            graphics.pose().pushMatrix();
            graphics.pose().translate(0.0f, -offset);
        }
    }

    @Inject(
            method = "extractHotbarAndDecorations",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/contextualbar/ContextualBarRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V")
    )
    private void afterRenderExperienceLevel(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci)
    {
        if (PlayerLocatorPlusClient.INSTANCE.getCurrentHudOffset() > 0)
        {
            graphics.pose().popMatrix();
        }
    }

    @Inject(
            method = "extractHotbarAndDecorations",
            at = @At(value = "HEAD")
    )
    private void beforeRenderChat(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci)
    {
        float offset = PlayerLocatorPlusClient.INSTANCE.getCurrentHudOffset();
        if (offset > 0)
        {
            graphics.pose().pushMatrix();
            graphics.pose().translate(0.0f, -offset);
        }
    }

    @Inject(
            method = "extractChat",
            at = @At(value = "RETURN")
    )
    private void afterRenderChat(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci)
    {
        if (PlayerLocatorPlusClient.INSTANCE.getCurrentHudOffset() > 0)
        {
            graphics.pose().popMatrix();
        }
    }

    @Inject(
            method = "nextContextualInfoState",
            at = @At(value = "RETURN"),
            cancellable = true
    )
    private void getCurrentBarType(CallbackInfoReturnable<Gui.ContextualInfo> cir)
    {
        // we hide the vanilla locator bar (so we can draw our own) when our bar should be visible
        // OR when the spectator menu is not open.
        // the vanilla locator bar is visible in spectator without the menu, while our users don't
        // want that (and I agree): https://github.com/timas130/PlayerLocatorPlus/issues/10
        boolean hideVanillaBarInSpectator =
                Objects.requireNonNull(this.minecraft.gameMode).getPlayerMode() == GameType.SPECTATOR
                        && !this.spectatorGui.isMenuActive();
        if (
                cir.getReturnValue() == Gui.ContextualInfo.LOCATOR
                        && (
                        PlayerLocatorPlusClient.INSTANCE.isBarVisible()
                                || hideVanillaBarInSpectator
                )
        )
        {
            // we don't need to account for the jump bar here, because the locator bar never
            // replaces it in vanilla code
            if (this.minecraft.gameMode.hasExperience())
            {
                cir.setReturnValue(Gui.ContextualInfo.EXPERIENCE);
            }
            else
            {
                cir.setReturnValue(Gui.ContextualInfo.EMPTY);
            }
        }
    }
}
