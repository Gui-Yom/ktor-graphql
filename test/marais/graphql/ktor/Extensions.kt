package marais.graphql.ktor

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.http.HttpHeaders
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import marais.graphql.ktor.data.Message

val mapper: ObjectMapper = ObjectMapper().registerModule(
    KotlinModule.Builder()
        .withReflectionCacheSize(512)
        .configure(KotlinFeature.NullToEmptyCollection, false)
        .configure(KotlinFeature.NullToEmptyMap, false)
        .configure(KotlinFeature.NullIsSameAsDefault, false)
        .configure(KotlinFeature.SingletonSupport, false)
        .configure(KotlinFeature.StrictNullChecks, false)
        .build()
)

inline fun <reified T : Message> Frame.isMessage(): Boolean = this is Frame.Text &&
        try {
            mapper.readValue(this.readText(), Message::class.java) is T
        } catch (e: JsonMappingException) {
            false
        } catch (e: Exception) {
            println("Other error : $e")
            false
        }

suspend fun SendChannel<Frame>.sendMessage(msg: Message) {
    this.send(
        Frame.Text(
            mapper.writeValueAsString(msg)
        )
    )
}

suspend inline fun <reified T : Message> ReceiveChannel<Frame>.receiveMessage(): T {
    return mapper.readValue((this.receive() as Frame.Text).readText(), Message::class.java) as T
}

fun TestApplicationEngine.makeGraphQLOverWSCall(
    uri: String,
    handler: suspend TestApplicationCall.(ReceiveChannel<Frame>, SendChannel<Frame>) -> Unit
) {
    handleWebSocketConversation(uri, {
        addHeader(HttpHeaders.SecWebSocketProtocol, "graphql-transport-ws")
    }, handler)
}
