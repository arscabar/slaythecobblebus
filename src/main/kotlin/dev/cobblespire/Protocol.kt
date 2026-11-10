package dev.cobblespire

import com.google.gson.JsonObject
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

data class SpirePayload(val json: String) : CustomPacketPayload {
    override fun type() = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<SpirePayload>(ResourceLocation.fromNamespaceAndPath("cobblespire", "message"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, SpirePayload> = StreamCodec.of(
            // Bounded adventure snapshots; server action requests are further limited to 8 KiB.
            { buf, value -> buf.writeUtf(value.json, 128000) },
            { buf -> SpirePayload(buf.readUtf(128000)) }
        )
        fun register() {
            PayloadTypeRegistry.playC2S().register(TYPE, CODEC)
            PayloadTypeRegistry.playS2C().register(TYPE, CODEC)
        }
    }
}

fun JsonObject.text(key: String, default: String = ""): String =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: default
fun JsonObject.number(key: String, default: Int = 0): Int =
    runCatching { get(key)?.asInt ?: default }.getOrDefault(default)
