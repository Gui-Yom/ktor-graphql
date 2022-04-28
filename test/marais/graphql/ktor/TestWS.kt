package marais.graphql.ktor

import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.charsets.*
import io.ktor.websocket.*
import marais.graphql.ktor.data.GraphQLRequest
import marais.graphql.ktor.data.GraphQLResponse
import marais.graphql.ktor.data.Message
import kotlin.test.Test
import kotlin.test.assertEquals

class TestWS {
    @Test
    fun testSimpleQuery(): Unit = testApplication {
        testAppModule()
        routing {
            graphqlWS()
        }

        graphqlWSSession {
            sendSerialized<Message>(Message.ConnectionInit())
            receiveDeserialized<Message.ConnectionAck>()
            sendSerialized<Message>(Message.Subscribe("69420", GraphQLRequest("query { number }")))
            assertEquals(
                Message.Next("69420", GraphQLResponse(mapOf("number" to 42))),
                receiveDeserialized<Message>(),
                "Expected response"
            )
            receiveDeserialized<Message.Complete>()
        }
    }

    @Test
    fun testSubscription(): Unit = testApplication {
        testAppModule()
        routing {
            graphqlWS()
        }

        graphqlWSSession {
            sendSerialized<Message>(Message.ConnectionInit())
            receiveDeserialized<Message.ConnectionAck>()
            sendSerialized<Message>(Message.Subscribe("69420", GraphQLRequest("subscription { number }")))
            for (i in 0..2) { // We should receive 3 Next messages
                assertEquals(
                    Message.Next("69420", GraphQLResponse(mapOf("number" to 42))),
                    receiveDeserialized<Message>()
                )
            }
            receiveDeserialized<Message.Complete>()
        }
    }

    @Test
    fun testPing(): Unit = testApplication {
        testAppModule()
        routing {
            graphqlWS()
        }

        graphqlWSSession {
            println(converter?.serialize(Charset.defaultCharset(), typeInfo<Message>(), Message.ConnectionInit())?.buffer?.decodeString())
            sendSerialized<Message>(Message.ConnectionInit())
            receiveDeserialized<Message.ConnectionAck>()
            sendSerialized<Message>(Message.Ping())
            receiveDeserialized<Message.Pong>()
        }
    }

    @Test
    fun testRefused(): Unit = testApplication {
        testAppModule()
        routing {
            graphqlWS {
                close()
                return@graphqlWS {}
            }
        }

        graphqlWSSession {
            sendSerialized<Message>(Message.ConnectionInit())
            receiveDeserialized<Message.ConnectionAck>()
            sendSerialized<Message>(Message.Ping())
            receiveDeserialized<Message.Pong>()
        }
    }
}
