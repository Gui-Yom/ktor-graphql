package marais.graphql.ktor

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.serialization.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JacksonContentConverter(val mapper: ObjectMapper) : WebsocketContentConverter {
    override suspend fun serialize(charset: Charset, typeInfo: TypeInfo, value: Any): Frame {
        return Frame.Text(mapper.writeValueAsString(value))
    }

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: Frame): Any {
        if (!isApplicable(content)) {
            throw WebsocketConverterNotFoundException("Unsupported frame ${content.frameType.name}")
        }
        try {
            return withContext(Dispatchers.IO) {
                val data = charset.newDecoder().decode(buildPacket { writeFully(content.data) })
                mapper.readValue(data, mapper.constructType(typeInfo.reifiedType))
            }
        } catch (deserializeFailure: Exception) {
            val convertException = JsonConvertException("Illegal json parameter found", deserializeFailure)

            when (deserializeFailure) {
                is JsonParseException -> throw convertException
                is JsonMappingException -> throw convertException
                else -> throw deserializeFailure
            }
        }
    }

    override fun isApplicable(frame: Frame): Boolean {
        return frame is Frame.Text || frame is Frame.Binary
    }
}
