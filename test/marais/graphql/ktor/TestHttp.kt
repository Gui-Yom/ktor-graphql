package marais.graphql.ktor

import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.http.HttpMethod
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import marais.graphql.ktor.data.GraphQLRequest
import marais.graphql.ktor.data.GraphQLResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class TestHttp {

    @Test
    fun testSimpleQuery() = withTestApplication({
        install(GraphQLEngine) {
            schema = testSchema
            mapper.registerModule(KotlinModule.Builder().build())
        }

        routing {
            graphql("/graphql") {
                // Do something before handling graphql like authentication
                println("yay ${this.context.request}")
                mapOf("extra" to "Yay !")
            }
        }
    }) {
        with(handleRequest(HttpMethod.Post, "/graphql") {
            val str = mapper.writeValueAsString(GraphQLRequest("query { number }"))
            setBody(str)
        }) {
            assertEquals(
                mapper.writeValueAsString(GraphQLResponse(mapOf("number" to 42))),
                response.content,
                "Expected response"
            )
        }
    }

    @Test
    fun testMultipleQueries() = withTestApplication({
        install(GraphQLEngine) {
            schema = testSchema
            mapper.registerModule(KotlinModule.Builder().build())
        }

        routing {
            graphql("/graphql") {
                // Do something before handling graphql like authentication
                println("Req ID : ${this.context.request.header("x-req-id")}")
                mapOf("extra" to "Passed !")
            }
        }
    }) {
        for (i in 0..10) {
            with(handleRequest(HttpMethod.Post, "/graphql") {
                addHeader("x-req-id", i.toString())
                val str = mapper.writeValueAsString(GraphQLRequest("query { number }"))
                setBody(str)
            }) {
                assertEquals(
                    mapper.writeValueAsString(GraphQLResponse(mapOf("number" to 42))),
                    response.content,
                    "Expected response"
                )
            }
        }
    }

    @Test
    fun testIntrospectionQuery() = withTestApplication({
        install(GraphQLEngine) {
            schema = testSchema
            mapper.registerModule(KotlinModule.Builder().build())
        }

        routing {
            graphql("/graphql") {
                mapOf("extra" to "Yay")
            }
        }
    }) {
        with(handleRequest(HttpMethod.Post, "/graphql") {
            val str = mapper.writeValueAsString(
                GraphQLRequest(
                    "query IntrospectionQuery { __schema { queryType { name } mutationType { name } subscriptionType { name } types { ...FullType } directives { name description locations args { ...InputValue          }        }      }    }    fragment FullType on __Type { kind name description fields(includeDeprecated: true) { name description args {          ...InputValue        }        type {          ...TypeRef        }        isDeprecated        deprecationReason      }      inputFields {        ...InputValue      }      interfaces {        ...TypeRef      }      enumValues(includeDeprecated: true) {        name        description        isDeprecated        deprecationReason      }      possibleTypes {        ...TypeRef      }    }    fragment InputValue on __InputValue {      name      description      type { ...TypeRef }      defaultValue    }    fragment TypeRef on __Type {      kind      name      ofType {        kind        name        ofType {          kind          name          ofType {            kind            name            ofType {              kind              name              ofType {                kind                name                ofType {                  kind                  name                  ofType {                    kind                    name                  }                }              }            }          }        }      }    }"
                )
            )
            setBody(str)
        }) {
            println(response.content)
        }
    }

    @Test
    fun testUnaccepted() = withTestApplication({
        install(GraphQLEngine) {
            schema = testSchema
            mapper.registerModule(KotlinModule.Builder().build())
        }

        routing {
            graphql("/graphql") {
                // Nope
                call.respond("""{"error":"Unauthorized"}""")
                null
            }
        }
    }) {
        with(handleRequest(HttpMethod.Post, "/graphql") {
            val str = mapper.writeValueAsString(GraphQLRequest("query { number }"))
            setBody(str)
        }) {
            assertEquals(
                """{"error":"Unauthorized"}""",
                response.content,
                "Expected response"
            )
        }
    }

    @Test
    fun testGraphQLContext() = withTestApplication({
        install(GraphQLEngine) {
            schema = testSchema
            mapper.registerModule(KotlinModule.Builder().build())
        }

        routing {
            graphql("/graphql") {
                // Set the graphql context based on some header value
                mapOf("x-req-id" to call.request.header("x-req-id")!!.toInt())
            }
        }
    }) {
        with(handleRequest(HttpMethod.Post, "/graphql") {
            val str = mapper.writeValueAsString(GraphQLRequest("query { envConsumer }"))
            setBody(str)
            addHeader("x-req-id", "1")
        }) {
            assertEquals(
                mapper.writeValueAsString(GraphQLResponse(mapOf("envConsumer" to 1))),
                response.content,
                "Expected response"
            )
        }
    }

    @Test
    fun testGraphQLContextInField() = withTestApplication({
        install(GraphQLEngine) {
            schema = testSchema
            mapper.registerModule(KotlinModule.Builder().build())
        }

        routing {
            graphql("/graphql") {
                // Set the graphql context based on some header value
                val secret = call.request.header("secret")?.toInt()
                if (secret != null) mapOf("secret" to secret) else mapOf<Any, Any>()
            }
        }
    }) {
        with(handleRequest(HttpMethod.Post, "/graphql") {
            val str = mapper.writeValueAsString(GraphQLRequest("query { restrictedInfo { restrictedField } }"))
            setBody(str)
            addHeader("secret", "42")
        }) {
            assertEquals(
                mapper.writeValueAsString(GraphQLResponse(mapOf("restrictedInfo" to mapOf("restrictedField" to "sensitive info")))),
                response.content,
                "Expected response"
            )
        }
        with(handleRequest(HttpMethod.Post, "/graphql") {
            val str = mapper.writeValueAsString(GraphQLRequest("query { restrictedInfo { restrictedField } }"))
            setBody(str)
            addHeader("secret", "10")
        }) {
            assertEquals(
                mapper.writeValueAsString(GraphQLResponse(mapOf("restrictedInfo" to mapOf("restrictedField" to null)))),
                response.content,
                "Expected response"
            )
        }
        with(handleRequest(HttpMethod.Post, "/graphql") {
            val str = mapper.writeValueAsString(GraphQLRequest("query { restrictedInfo { restrictedField } }"))
            setBody(str)
        }) {
            assertEquals(
                mapper.writeValueAsString(GraphQLResponse(mapOf("restrictedInfo" to mapOf("restrictedField" to null)))),
                response.content,
                "Expected response"
            )
        }
    }
}