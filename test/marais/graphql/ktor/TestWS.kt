package marais.graphql.ktor

import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.application.install
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.routing.routing
import io.ktor.server.testing.withTestApplication
import marais.graphql.ktor.data.GraphQLRequest
import marais.graphql.ktor.data.GraphQLResponse
import marais.graphql.ktor.data.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TestWS {

    @Test
    fun testSimpleQuery(): Unit = withTestApplication({

        install(io.ktor.websocket.WebSockets)

        install(GraphQLEngine) {
            schema = testSchema
            mapper.registerModule(KotlinModule.Builder().build())
        }

        routing {
            graphqlWS("/graphql") {
                mapOf("extra" to "Yay !")
            }
        }
    }) {
        makeGraphQLOverWSCall("/graphql") { incoming, outgoing ->
            outgoing.sendMessage(Message.ConnectionInit())
            // ACK
            incoming.receive()
            outgoing.sendMessage(Message.Subscribe("69420", GraphQLRequest("query { number }")))
            val response = incoming.receive() as Frame.Text
            assertEquals(
                mapper.writeValueAsString(
                    Message.Next("69420", GraphQLResponse(mapOf("number" to 42)))
                ),
                response.readText(),
                "Expected response"
            )
            // Complete
            assertIs<Message.Complete>(incoming.receiveMessage())
        }
    }

    @Test
    fun testSubscription(): Unit = withTestApplication({

        install(io.ktor.websocket.WebSockets)

        install(GraphQLEngine) {
            schema = testSchema
            mapper.registerModule(KotlinModule.Builder().build())
        }

        routing {
            graphqlWS("/graphql") {
                mapOf("extra" to "Yay !")
            }
        }
    }) {
        makeGraphQLOverWSCall("/graphql") { incoming, outgoing ->
            outgoing.sendMessage(Message.ConnectionInit())

            assertTrue(incoming.receive().isMessage<Message.ConnectionAck>())

            outgoing.sendMessage(Message.Subscribe("69420", GraphQLRequest("subscription { number }")))
            for (i in 0..2) { // We should receive 3 Next messages
                assertEquals(
                    (incoming.receive() as Frame.Text).readText(),
                    mapper.writeValueAsString(
                        Message.Next("69420", GraphQLResponse(mapOf("number" to 42)))
                    )
                )
            }
            // Complete
            assertTrue(incoming.receive().isMessage<Message.Complete>())
        }
    }

    @Test
    fun testPing(): Unit = withTestApplication({

        install(io.ktor.websocket.WebSockets)

        install(GraphQLEngine) {
            schema = testSchema
            mapper.registerModule(KotlinModule.Builder().build())
        }

        routing {
            graphqlWS("/graphql") {
                mapOf("extra" to "Yay !")
            }
        }
    }) {
        makeGraphQLOverWSCall("/graphql") { incoming, outgoing ->
            outgoing.sendMessage(Message.ConnectionInit())

            assertTrue(incoming.receive().isMessage<Message.ConnectionAck>())

            outgoing.sendMessage(Message.Ping())
            assertTrue(incoming.receive().isMessage<Message.Pong>())
        }
    }
}