package sh.sit.plp.mixin;

import me.shedaniel.autoconfig.gui.registry.ComposedGuiRegistryAccess;
import me.shedaniel.autoconfig.gui.registry.DefaultGuiRegistryAccess;
import me.shedaniel.autoconfig.gui.registry.GuiRegistry;
import me.shedaniel.autoconfig.gui.registry.api.GuiRegistryAccess;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.List;

@Mixin(value = {ComposedGuiRegistryAccess.class, DefaultGuiRegistryAccess.class, GuiRegistry.class}, remap = false)
public class AutoconfigGuiRegistryAccessMixin {
    @Inject(method = "get", at = @At("RETURN"), cancellable = true)
    private void get(String i18n, Field field, Object config, Object defaults, GuiRegistryAccess registry, CallbackInfoReturnable<List<AbstractConfigListEntry>> cir) {
        if (field.getName().equals("$childSerializers")) {
            cir.setReturnValue(List.of());
        }
    }
}
