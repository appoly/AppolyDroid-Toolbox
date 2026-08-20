package uk.co.appoly.droid.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A serializer for nullable enum fields that returns null for unknown/empty string values
 * instead of throwing an exception.
 *
 * Use this for nullable enum fields where the backend might send empty or unknown values.
 *
 * Both null sources are handled: a literal `null` token on the wire decodes to null, and a
 * non-null value the [deserialize] lambda does not recognise also decodes to null.
 *
 * The [descriptor] is marked nullable, which is what stops kotlinx wrapping this
 * `KSerializer<T?>` in its own `NullableSerializer` on the property path — so this class does
 * its own null handling and behaves identically whether it is used via
 * `@Serializable(with = ...)` or directly (`Json.decodeFromString(serializer, "null")`,
 * `ListSerializer(serializer)`).
 */
open class NullableEnumAsStringSerializer<T : Enum<*>>(
	serialName: String,
	val serialize: (v: T) -> String,
	val deserialize: (v: String) -> T?
) : KSerializer<T?> {
	override val descriptor: SerialDescriptor =
		PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING).nullable

	@OptIn(ExperimentalSerializationApi::class)
	override fun serialize(encoder: Encoder, value: T?) {
		if (value != null) {
			encoder.encodeString(serialize(value))
		} else {
			encoder.encodeNull()
		}
	}

	@OptIn(ExperimentalSerializationApi::class)
	override fun deserialize(decoder: Decoder): T? =
		if (decoder.decodeNotNullMark()) {
			deserialize(decoder.decodeString())
		} else {
			decoder.decodeNull()
		}
}