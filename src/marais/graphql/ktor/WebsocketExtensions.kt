package marais.graphql.ktor

import io.ktor.http.cio.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import marais.graphql.ktor.data.Message

fun Frame.toMessage(json: Json): Message {
    if (this is Frame.Text) {
        return json.decodeFromString(Message.serializer(), readText())
    } else {
        throw BadFrameException
    }
}

suspend fun <T : Message> DefaultWebSocketServerSession.sendMessage(json: Json, msg: T) {
    this.send(
        Frame.Text(
            json.encodeToString(
                Message.serializer(),
                msg
            )
        )
    )
}
