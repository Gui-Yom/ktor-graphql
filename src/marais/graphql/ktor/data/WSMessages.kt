package marais.graphql.ktor.data

import kotlinx.serialization.Contextual
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
    data class ConnectionInit(val payload: Map<String, @Contextual Any>? = null) : Message()

    @Serializable
    @SerialName("connection_ack")
    data class ConnectionAck(val payload: Map<String, @Contextual Any>? = null) : Message()

    @Serializable
    @SerialName("subscribe")
    data class Subscribe(val id: ID, val payload: GraphQLRequest) : Message()

    @Serializable
    @SerialName("next")
    data class Next(val id: ID, val payload: GraphQLResponse) : Message()

    @Serializable
    @SerialName("error")
    data class Error(val id: ID, val payload: String) : Message()

    @Serializable
    @SerialName("complete")
    data class Complete(val id: ID) : Message()
}
