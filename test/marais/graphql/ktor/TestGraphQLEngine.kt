package marais.graphql.ktor

import com.expediagroup.graphql.generator.SchemaGenerator
import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import marais.graphql.ktor.types.GraphQLRequest
import marais.graphql.ktor.types.GraphQLResponse
import marais.graphql.ktor.types.Message
import kotlin.test.Test
import kotlin.test.assertEquals

class TestGraphQLEngine {

    val json = Json

    object Query {
        fun query(): Int = 42
    }

    @Test
    fun testGraphQLFeature() = withTestApplication({
        install(GraphQLEngine) {
            this.json = this@TestGraphQLEngine.json
            graphqlConfig {
                schema(
                    SchemaGenerator(SchemaGeneratorConfig(listOf("marais.graphql.ktor"))).generateSchema(
                        listOf(TopLevelObject(Query))
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
        with(handleRequest(HttpMethod.Post, "/graphql") {
            val str = json.encodeToString(GraphQLRequest.serializer(), GraphQLRequest("query { query }"))
            setBody(str)
        }) {
            assertEquals(
                json.encodeToString(GraphQLResponse.serializer(), GraphQLResponse(mapOf("query" to 42))),
                response.content,
                "Expected response"
            )
        }
    }

    @Test
    fun testGraphQLWS(): Unit = withTestApplication({

        install(WebSockets)

        install(GraphQLEngine) {
            this.json = this@TestGraphQLEngine.json
            allowGraphQLOverWS = true
            graphqlConfig {
                schema(
                    SchemaGenerator(SchemaGeneratorConfig(listOf("marais.graphql.ktor"))).generateSchema(
                        listOf(TopLevelObject(Query))
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
                        Message.Subscribe("69420", GraphQLRequest("query { query }"))
                    )
                )
            )
            val response = incoming.receive() as Frame.Text
            assertEquals(
                json.encodeToString(Message.serializer(), Message.Next("69420", GraphQLResponse(mapOf("query" to 42)))),
                response.readText(),
                "Expected response"
            )
            // Complete
            incoming.receive()
        }
    }
}