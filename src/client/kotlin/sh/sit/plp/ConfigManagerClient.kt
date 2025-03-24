package sh.sit.plp

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import sh.sit.plp.network.ModConfigS2CPayload

object ConfigManagerClient {
    fun init() {
        ClientPlayNetworking.registerGlobalReceiver(ModConfigS2CPayload.TYPE) { payload, _, _ ->
            ConfigManager.configOverride = payload.config
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            ConfigManager.configOverride = null
        }
    }
}
