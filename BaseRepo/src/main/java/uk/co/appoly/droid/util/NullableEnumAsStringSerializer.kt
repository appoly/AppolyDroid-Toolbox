package uk.co.appoly.droid.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A serializer for nullable enum fields that returns null for unknown/empty string values
 * instead of throwing an exception.
 *
 * Use this for nullable enum fields where the backend might send empty or unknown values.
 */
open class NullableEnumAsStringSerializer<T : Enum<*>>(
	serialName: String,
	val serialize: (v: T) -> String,
	val deserialize: (v: String) -> T?
) : KSerializer<T?> {
	override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

	@OptIn(ExperimentalSerializationApi::class)
	override fun serialize(encoder: Encoder, value: T?) {
		if (value != null) {
			encoder.encodeString(serialize(value))
		} else {
			encoder.encodeNull()
		}
	}

	override fun deserialize(decoder: Decoder): T? {
		val v = decoder.decodeString()
		return deserialize(v)
	}
}