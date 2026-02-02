package sh.sit.plp

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.AutoConfigClient
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import sh.sit.plp.config.ModConfig

object ModMenuEntrypoint : ModMenuApi {
    @Environment(EnvType.CLIENT)
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> = ConfigScreenFactory { parent ->
        AutoConfigClient.getConfigScreen(ModConfig::class.java, parent).get()
    }
}
