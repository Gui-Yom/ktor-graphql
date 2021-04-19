package marais.graphql.ktor

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.cio.websocket.*
import marais.graphql.ktor.data.Message

suspend fun WebSocketSession.sendMessage(mapper: ObjectMapper, msg: Message) {
    this.send(Frame.Text(mapper.writeValueAsString(msg)))
}

suspend fun WebSocketSession.receiveMessage(mapper: ObjectMapper): Message {
    return this.incoming.receive().toMessage(mapper)
}

fun Frame.toMessage(mapper: ObjectMapper): Message {
    return when (this) {
        is Frame.Text -> mapper.readValue(this.readText(), Message::class.java)
        else -> throw BadFrameException
    }
}
