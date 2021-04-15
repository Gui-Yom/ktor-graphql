package marais.graphql.ktor.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import kotlin.reflect.full.starProjectedType

@ExperimentalSerializationApi
object AnyMapDescriptor : SerialDescriptor {
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
            1 -> dummyDescriptor
            else -> error("Unreached")
        }
    }
}

@ExperimentalSerializationApi
object AnyListDescriptor : SerialDescriptor {
    override val serialName: String = "AnyMap"
    override val kind: SerialKind get() = StructureKind.LIST
    override val elementsCount: Int = 1
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
        return dummyDescriptor
    }
}

val dummyDescriptor = PrimitiveSerialDescriptor("any", PrimitiveKind.STRING)

object AnyTreeSerializer : KSerializer<Any?> {
    // Dummy
    override val descriptor: SerialDescriptor = dummyDescriptor
    private val stringSerializer = String.serializer()

    @ExperimentalSerializationApi
    override fun serialize(encoder: Encoder, value: Any?) {
        if (value != null) {
            val valueClass = value::class
            val valueType = valueClass.starProjectedType

            // Primitives
            if (value is Number || value is String || value is Boolean) {
                encoder.encodeSerializableValue(serializer(valueType), value)
            } else if (value is Map<*, *>) {
                val composite = encoder.beginStructure(AnyMapDescriptor)
                var index = 0
                value.forEach { (k, v) ->
                    composite.encodeSerializableElement(AnyMapDescriptor, index++, stringSerializer, k.toString())
                    composite.encodeSerializableElement(AnyMapDescriptor, index++, AnyTreeSerializer, v)
                }
                composite.endStructure(AnyMapDescriptor)
            } else if (value is List<*>) {
                val composite = encoder.beginStructure(AnyListDescriptor)
                value.forEachIndexed { index, v ->
                    composite.encodeSerializableElement(AnyListDescriptor, index, AnyTreeSerializer, v)
                }
                composite.endStructure(AnyListDescriptor)
            }
        } else {
            encoder.encodeNull()
        }
    }

    override fun deserialize(decoder: Decoder): Any? {
        throw UnsupportedOperationException("Only serialization is supported")
    }
}
