package sh.sit.plp

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import me.shedaniel.autoconfig.AutoConfig
import sh.sit.plp.config.ModConfig

object ModMenuEntrypoint : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> = ConfigScreenFactory { parent ->
        AutoConfig.getConfigScreen(ModConfig::class.java, parent).get()
    }
}
