package uk.co.appoly.droid.s3upload.multipart.network.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import uk.co.appoly.droid.s3upload.utils.StringOrListSerialiser

/**
 * Serializer that handles a Map<String, String> which may be returned as:
 * - An empty JSON array `[]` - deserializes to empty map
 * - A JSON object `{"key": "value"}` - deserializes normally
 *
 * This is needed because some APIs return `"headers": []` for empty headers
 * instead of `"headers": {}`.
 */
object EmptyArrayAsEmptyMapSerializer : KSerializer<Map<String, String>> {
    private val mapSerializer = MapSerializer(String.serializer(), StringOrListSerialiser)

    override val descriptor: SerialDescriptor = mapSerializer.descriptor

    override fun deserialize(decoder: Decoder): Map<String, String> {
        val input = decoder as JsonDecoder
        return when (val element = input.decodeJsonElement()) {
            is JsonArray -> {
                // Empty array returns empty map
                // Non-empty array could be array of key-value pairs, but treat as empty for safety
                emptyMap()
            }
            is JsonObject -> {
                // Normal map deserialization
                element.mapValues { (_, value) ->
                    when (value) {
                        is JsonArray -> value.joinToString { it.jsonPrimitive.content }
                        else -> value.jsonPrimitive.content
                    }
                }
            }
            else -> emptyMap()
        }
    }

    override fun serialize(encoder: Encoder, value: Map<String, String>) {
        mapSerializer.serialize(encoder, value)
    }
}
