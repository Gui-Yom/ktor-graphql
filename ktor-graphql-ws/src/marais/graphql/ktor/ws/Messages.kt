package marais.graphql.ktor.ws

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias ID = String

/*
WARNING ! Must use the more generic Message.serializer() to ser/deser every message types.
This is required to correctly encode 'type' discriminator.
 */

@Serializable
sealed class Message {
    @Serializable
    @SerialName("connection_init")
    data class ConnectionInit(val payload: Map<String, String>?) : Message()

    @Serializable
    @SerialName("connection_ack")
    data class ConnectionAck(val payload: Map<String, String>?) : Message()

    @Serializable
    @SerialName("subscribe")
    data class Subscribe(val id: ID, val payload: SubscribePayload) : Message()

    @Serializable
    @SerialName("next")
    data class Next(val id: ID, val payload: String) : Message()

    @Serializable
    @SerialName("error")
    data class Error(val id: ID, val payload: String) : Message()

    @Serializable
    @SerialName("complete")
    data class Complete(val id: ID) : Message()
}

@Serializable
data class SubscribePayload(val operationName: String?, val query: String, val variables: Map<String, String>?)
