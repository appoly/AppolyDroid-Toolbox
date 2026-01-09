package uk.co.appoly.droid.s3upload.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.collections.joinToString

object StringOrListSerialiser : KSerializer<String> {
    override val descriptor: SerialDescriptor = String.serializer().descriptor

    override fun deserialize(decoder: Decoder): String {
        val input = decoder as JsonDecoder
        return when (val element = input.decodeJsonElement()) {
            is JsonArray -> element.joinToString { it.jsonPrimitive.content }
            is JsonPrimitive -> element.content
            else -> ""
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}