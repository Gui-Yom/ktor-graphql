package marais.graphql.ktor.data

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.ktor.http.cio.websocket.*

/*
Websocket messages as per the spec : https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md
Last review : 27-08-2021
 */

typealias ID = String
typealias Record = Map<String, Any?>

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = Message.ConnectionInit::class, name = "connection_init"),
    JsonSubTypes.Type(value = Message.ConnectionAck::class, name = "connection_ack"),
    JsonSubTypes.Type(value = Message.Ping::class, name = "ping"),
    JsonSubTypes.Type(value = Message.Pong::class, name = "pong"),
    JsonSubTypes.Type(value = Message.Subscribe::class, name = "subscribe"),
    JsonSubTypes.Type(value = Message.Next::class, name = "next"),
    JsonSubTypes.Type(value = Message.Error::class, name = "error"),
    JsonSubTypes.Type(value = Message.Complete::class, name = "complete"),
)
sealed class Message {
    /**
     * Indicates that the client wants to establish a connection within the existing socket.
     */
    data class ConnectionInit(val payload: Record? = null) : Message()

    /**
     * Expected response to the ConnectionInit message from the client acknowledging a successful connection with the server.
     */
    data class ConnectionAck(val payload: Record? = null) : Message()

    /**
     * Useful for detecting failed connections, displaying latency metrics or other types of network probing.
     */
    data class Ping(val payload: Record? = null) : Message()

    /**
     * The response to the Ping message. Must be sent as soon as the Ping message is received.
     */
    data class Pong(val payload: Record? = null) : Message()

    /**
     * Requests an operation specified in the message payload.
     * This message provides a unique ID field to connect published messages to the operation requested by this message.
     */
    data class Subscribe(val id: ID, val payload: GraphQLRequest) : Message()

    /**
     * Operation execution result(s) from the source stream created by the binding Subscribe message.
     */
    data class Next(val id: ID, val payload: GraphQLResponse) : Message()

    /**
     * Operation execution error(s) triggered by the Next message happening before the actual execution, usually due to validation errors.
     */
    data class Error(val id: ID, val payload: String) : Message()

    /**
     * - Server -> Client indicates that the requested operation execution has completed.
     * If the server dispatched the Error message relative to the original Subscribe message, no Complete message will be emitted.
     * - Client -> Server indicates that the client has stopped listening and wants to complete the subscription.
     * No further events, relevant to the original subscription, should be sent through. Even if the client completed a single result operation before it resolved, the result should not be sent through once it does.
     */
    data class Complete(val id: ID) : Message()
}

object CloseReasons {
    val UNEXPECTED_MESSAGE = CloseReason(4400, "Unexpected message")
    val UNAUTHORIZED = CloseReason(4401, "Unauthorized")
    val INIT_TIMEOUT = CloseReason(4408, "Connection initialisation timeout")
    fun ALREADY_SUBSCRIBED(id: String) = CloseReason(4409, "Subscriber for $id already exists")
    val ALREADY_INIT = CloseReason(4429, "Too many initialisation requests")
}
