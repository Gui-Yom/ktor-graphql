package marais.graphql.ktor

import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.execution.FlowSubscriptionExecutionStrategy
import com.expediagroup.graphql.generator.hooks.FlowSubscriptionSchemaGeneratorHooks
import com.expediagroup.graphql.generator.toSchema
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.serialization.*
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
            this.json = marais.graphql.ktor.json
            allowGraphQLOverWS = true
            graphqlConfig {
                schema(
                    toSchema(
                        config = SchemaGeneratorConfig(listOf("marais.graphql.ktor")),
                        queries = listOf(TopLevelObject(Query))
                    )
                )
            }
        }

        install(ContentNegotiation) {
            json(json)
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
            outgoing.send(Frame.Text(json.encodeToString(Message.serializer(), Message.ConnectionInit(null))))
            // ACK
            incoming.receive()
            outgoing.send(
                Frame.Text(
                    json.encodeToString(
                        Message.serializer(),
                        Message.Subscribe("69420", GraphQLRequest("query { number }"))
                    )
                )
            )
            val response = incoming.receive() as Frame.Text
            assertEquals(
                json.encodeToString(
                    Message.serializer(),
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
            this.json = marais.graphql.ktor.json
            allowGraphQLOverWS = true
            graphqlConfig {
                schema(
                    toSchema(
                        config = SchemaGeneratorConfig(
                            supportedPackages = listOf("marais.graphql.ktor"),
                            hooks = FlowSubscriptionSchemaGeneratorHooks()
                        ),
                        queries = listOf(TopLevelObject(Query)),
                        subscriptions = listOf(TopLevelObject(Subscription))
                    )
                )
                // Required so we can use kotlin coroutines' Flow<T> directly in the schema
                subscriptionExecutionStrategy(FlowSubscriptionExecutionStrategy())
            }
        }

        install(ContentNegotiation) {
            json(json)
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
                    json.encodeToString(
                        Message.serializer(),
                        Message.Next("69420", GraphQLResponse(mapOf("number" to 42)))
                    )
                )
            }
            // Complete
            assertTrue(incoming.receive().isMessage<Message.Complete>())
        }
    }
}