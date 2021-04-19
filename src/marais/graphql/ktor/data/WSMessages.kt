package marais.graphql.ktor.data

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo


typealias ID = String

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = Message.ConnectionInit::class, name = "connection_init"),
    JsonSubTypes.Type(value = Message.ConnectionAck::class, name = "connection_ack"),
    JsonSubTypes.Type(value = Message.Subscribe::class, name = "subscribe"),
    JsonSubTypes.Type(value = Message.Next::class, name = "next"),
    JsonSubTypes.Type(value = Message.Error::class, name = "error"),
    JsonSubTypes.Type(value = Message.Complete::class, name = "complete"),
)
sealed class Message {
    data class ConnectionInit(val payload: Map<String, Any>? = null) : Message()
    data class ConnectionAck(val payload: Map<String, Any>? = null) : Message()
    data class Subscribe(val id: ID, val payload: GraphQLRequest) : Message()
    data class Next(val id: ID, val payload: GraphQLResponse) : Message()
    data class Error(val id: ID, val payload: String) : Message()
    data class Complete(val id: ID) : Message()
}
