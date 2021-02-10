package marais.graphql.ktor.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import kotlin.reflect.full.starProjectedType

object AnyValueSerializer : KSerializer<Any?> {
    // Dummy
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("any", PrimitiveKind.STRING)

    @ExperimentalSerializationApi
    override fun serialize(encoder: Encoder, value: Any?) {
        if (value != null) {
            val valueClass = value::class
            val valueType = valueClass.starProjectedType

            // Primitives
            if (value is Number || value is String || value is Boolean) {
                encoder.encodeSerializableValue(serializer(valueType), value)
            } else if (value is Map<*, *>) {
                encoder.encodeSerializableValue(AnyMapSerializer, value as Map<String, Any?>)
            }
        } else {
            encoder.encodeNull()
        }
    }

    override fun deserialize(decoder: Decoder): Any? {
        throw UnsupportedOperationException("Only serialization is supported")
    }
}

@ExperimentalSerializationApi
class AnyMapDescriptor(
    val valueDescriptor: SerialDescriptor
) : SerialDescriptor {
    val keyDescriptor: SerialDescriptor = PrimitiveSerialDescriptor("AnyMapKey", PrimitiveKind.STRING)
    override val serialName: String = "AnyMap"
    override val kind: SerialKind get() = StructureKind.MAP
    override val elementsCount: Int = 2
    override fun getElementName(index: Int): String = index.toString()
    override fun getElementIndex(name: String): Int =
        name.toIntOrNull() ?: throw IllegalArgumentException("$name is not a valid map index")

    override fun isElementOptional(index: Int): Boolean {
        require(index >= 0) { "Illegal index $index, $serialName expects only non-negative indices" }
        return false
    }

    override fun getElementAnnotations(index: Int): List<Annotation> {
        require(index >= 0) { "Illegal index $index, $serialName expects only non-negative indices" }
        return emptyList()
    }

    override fun getElementDescriptor(index: Int): SerialDescriptor {
        require(index >= 0) { "Illegal index $index, $serialName expects only non-negative indices" }
        return when (index % 2) {
            0 -> keyDescriptor
            1 -> valueDescriptor
            else -> error("Unreached")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AnyMapDescriptor) return false
        if (serialName != other.serialName) return false
        if (keyDescriptor != other.keyDescriptor) return false
        if (valueDescriptor != other.valueDescriptor) return false
        return true
    }

    override fun hashCode(): Int {
        var result = serialName.hashCode()
        result = 31 * result + keyDescriptor.hashCode()
        result = 31 * result + valueDescriptor.hashCode()
        return result
    }

    override fun toString(): String = "$serialName($keyDescriptor, $valueDescriptor)"
}

/**
 * These maps are used a lot in graphql since the data we return is variable. This poses no problem on deserialization but we can't serialize these without relying on reflection.
 */
object AnyMapSerializer : KSerializer<Map<String, Any?>> {

    // Dummy
    override val descriptor = AnyMapDescriptor(AnyValueSerializer.descriptor)

    override fun serialize(encoder: Encoder, value: Map<String, Any?>) {
        val composite = encoder.beginStructure(descriptor)
        var index = 0
        value.forEach { (k, v) ->
            composite.encodeSerializableElement(descriptor, index++, String.serializer(), k)
            composite.encodeSerializableElement(descriptor, index++, AnyValueSerializer, v)
        }
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): Map<String, Any?> {
        throw UnsupportedOperationException("Only serialization is supported")
    }
}
