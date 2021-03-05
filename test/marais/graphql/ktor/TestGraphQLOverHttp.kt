package marais.graphql.ktor

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.testing.*
import marais.graphql.ktor.data.GraphQLRequest
import marais.graphql.ktor.data.GraphQLResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class TestGraphQLOverHttp {

    @Test
    fun testSimpleQuery() = withTestApplication({
        install(GraphQLEngine) {
            this.json = marais.graphql.ktor.json
            schema = testSchema
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
            val str = json.encodeToString(GraphQLRequest.serializer(), GraphQLRequest("query { number }"))
            setBody(str)
        }) {
            assertEquals(
                json.encodeToString(GraphQLResponse.serializer(), GraphQLResponse(mapOf("number" to 42))),
                response.content,
                "Expected response"
            )
        }
    }
}