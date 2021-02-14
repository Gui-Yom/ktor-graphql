package marais.graphql.ktor

import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.server.testing.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import marais.graphql.ktor.data.Message

val json = Json

inline fun <reified T : Message> Frame.isMessage(): Boolean = this is Frame.Text &&
        try {
            json.decodeFromString(Message.serializer(), this.readText()) is T
        } catch (e: SerializationException) {
            false
        }

suspend inline fun <reified T : Message> SendChannel<Frame>.sendMessage(msg: T) {
    this.send(
        Frame.Text(
            json.encodeToString(
                Message.serializer(),
                msg
            )
        )
    )
}

suspend inline fun <reified T : Message> ReceiveChannel<Frame>.receiveMessage(): T {
    return json.decodeFromString(Message.serializer(), (this.receive() as Frame.Text).readText()) as T
}

fun TestApplicationEngine.makeGraphQLOverWSCall(
    uri: String,
    handler: suspend TestApplicationCall.(ReceiveChannel<Frame>, SendChannel<Frame>) -> Unit
) {
    handleWebSocketConversation(uri, {
        addHeader(HttpHeaders.SecWebSocketProtocol, "graphql-transport-ws")
    }, handler)
}
