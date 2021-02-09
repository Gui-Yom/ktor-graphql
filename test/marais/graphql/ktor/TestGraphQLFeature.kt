package marais.graphql.ktor

import com.expediagroup.graphql.generator.TopLevelObject
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import marais.graphql.ktor.types.GraphQLRequest
import marais.graphql.ktor.types.GraphQLResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class TestGraphQLFeature {

    object Query {
        fun query(): Int = 42
    }

    @Test
    fun testGraphQLFeature() = withTestApplication({

        install(GraphQLFeature) {
            queries = listOf(TopLevelObject(Query))
        }

        install(ContentNegotiation) {
            json(Json)
        }

        routing {
            graphql("/graphql") {
                // Do something before handling graphql like authentication
            }
        }
    }) {
        with(handleRequest(HttpMethod.Post, "/graphql") {
            val str = Json.encodeToString(GraphQLRequest.serializer(), GraphQLRequest("query { query }"))
            setBody(str)
        }) {
            assertEquals(
                Json.encodeToString(GraphQLResponse.serializer(), GraphQLResponse(mapOf("query" to 42))),
                response.content,
                "Expected response"
            )
        }
    }
}