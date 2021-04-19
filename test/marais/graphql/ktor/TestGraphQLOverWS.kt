package marais.graphql.ktor

import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.jackson.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import marais.graphql.ktor.data.GraphQLRequest
import marais.graphql.ktor.data.GraphQLResponse
import marais.graphql.ktor.data.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestGraphQLOverWS {

    @Test
    fun testSimpleQuery(): Unit = withTestApplication({

        install(io.ktor.websocket.WebSockets)

        install(GraphQLEngine) {
            allowGraphQLOverWS = true
            schema = testSchema
        }

        install(ContentNegotiation) {
            jackson {
                registerModule(KotlinModule())
            }
        }

        routing {
            graphql("/graphql") {
                // Do something before handling graphql like authentication
            }
        }
    }) {
        handleWebSocketConversation("/graphql", {
            addHeader(HttpHeaders.SecWebSocketProtocol, "graphql-transport-ws")
        }) { incoming, outgoing ->
            outgoing.sendMessage(Message.ConnectionInit(null))
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
            incoming.receive()
        }
    }

    @Test
    fun testSubscription(): Unit = withTestApplication({

        install(io.ktor.websocket.WebSockets)

        install(GraphQLEngine) {
            allowGraphQLOverWS = true
            schema = testSchema
        }

        install(ContentNegotiation) {
            jackson {
                registerModule(KotlinModule())
            }
        }

        routing {
            graphql("/graphql") {
                // Do something before handling graphql like authentication
            }
        }
    }) {
        makeGraphQLOverWSCall("/graphql") { incoming, outgoing ->
            outgoing.sendMessage(Message.ConnectionInit(null))

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
}