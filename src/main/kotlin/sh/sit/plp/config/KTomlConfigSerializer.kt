package sh.sit.plp.config

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.shedaniel.autoconfig.annotation.Config
import me.shedaniel.autoconfig.serializer.ConfigSerializer
import me.shedaniel.autoconfig.serializer.ConfigSerializer.SerializationException
import me.shedaniel.autoconfig.util.Utils
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class KTomlConfigSerializer(
    private val definition: Config,
    private val configClass: Class<ModConfig>,
) : ConfigSerializer<ModConfig> {
    private val toml = Toml(
        inputConfig = TomlInputConfig(
            ignoreUnknownNames = true,
        ),
    )

    private fun getConfigPath(): Path {
        return Utils.getConfigFolder().resolve("${definition.name}.toml")
    }

    override fun serialize(config: ModConfig) {
        val configPath = getConfigPath()

        try {
            val configString = toml.encodeToString(config)
            // this calls close() under the hood
            configPath.toFile().writeText(configString)
        } catch (e: IOException) {
            throw SerializationException(e)
        }
    }

    override fun deserialize(): ModConfig {
        val configPath = getConfigPath()
        if (Files.exists(configPath)) {
            try {
                val configString = configPath.toFile().readText()
                val config = toml.decodeFromString<ModConfig>(configString)
                return config
            } catch (e: IOException) {
                throw SerializationException(e)
            } catch (e: kotlinx.serialization.SerializationException) {
                throw SerializationException(e)
            }
        } else {
            return createDefault()
        }
    }

    override fun createDefault(): ModConfig {
        return Utils.constructUnsafely(this.configClass)
    }
}
