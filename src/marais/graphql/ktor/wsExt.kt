package marais.graphql.ktor

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.WebSocketSession
import io.ktor.http.cio.websocket.readText
import marais.graphql.ktor.data.Message

internal suspend fun WebSocketSession.sendMessage(mapper: ObjectMapper, msg: Message) {
    this.send(Frame.Text(mapper.writeValueAsString(msg)))
}

internal suspend fun WebSocketSession.receiveMessage(mapper: ObjectMapper): Message {
    return this.incoming.receive().toMessage(mapper)
}

internal fun Frame.toMessage(mapper: ObjectMapper): Message {
    return when (this) {
        is Frame.Text -> mapper.readValue(this.readText(), Message::class.java)
        else -> throw BadFrameException
    }
}
