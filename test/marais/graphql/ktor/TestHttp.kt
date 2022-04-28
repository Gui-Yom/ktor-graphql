package marais.graphql.ktor

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import marais.graphql.ktor.data.GraphQLRequest
import marais.graphql.ktor.data.GraphQLResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestHttp {

    @Test
    fun testSimpleQuery() = testApplication {
        testAppModule()
        routing {
            graphql()
        }

        val res = testClient().post("/graphql") {
            setBody(GraphQLRequest("query { number }"))
        }
        assertEquals(GraphQLResponse(mapOf("number" to 42)), res.body(), "Expected response")
    }

    @Test
    fun testMultipleQueries() = testApplication {
        testAppModule()
        routing {
            graphql {
                // Do something before handling graphql like authentication
                println("Req ID : ${it.call.request.header("x-req-id")}")
                mapOf("extra" to "Passed !")
            }
        }
        val client = testClient()

        for (i in 0..10) {
            val res = client.post("/graphql") {
                header("x-req-id", i.toString())
                setBody(GraphQLRequest("query { number }"))
            }
            assertEquals(GraphQLResponse(mapOf("number" to 42)), res.body(), "Expected response")
        }
    }

    @Test
    fun testIntrospectionQuery() = testApplication {
        testAppModule()
        routing {
            graphql()
        }
        val client = testClient()

        val response = client.post("/graphql") {
            setBody(
                GraphQLRequest(
                    "query IntrospectionQuery { __schema { queryType { name } mutationType { name } subscriptionType { name } types { ...FullType } directives { name description locations args { ...InputValue          }        }      }    }    fragment FullType on __Type { kind name description fields(includeDeprecated: true) { name description args {          ...InputValue        }        type {          ...TypeRef        }        isDeprecated        deprecationReason      }      inputFields {        ...InputValue      }      interfaces {        ...TypeRef      }      enumValues(includeDeprecated: true) {        name        description        isDeprecated        deprecationReason      }      possibleTypes {        ...TypeRef      }    }    fragment InputValue on __InputValue {      name      description      type { ...TypeRef }      defaultValue    }    fragment TypeRef on __Type {      kind      name      ofType {        kind        name        ofType {          kind          name          ofType {            kind            name            ofType {              kind              name              ofType {                kind                name                ofType {                  kind                  name                  ofType {                    kind                    name                  }                }              }            }          }        }      }    }"
                )
            )
        }
        println(response.bodyAsText())
    }

    @Test
    fun testUnaccepted() = testApplication {
        testAppModule()
        routing {
            graphql {
                // Nope
                it.call.respond("""{"error":"Unauthorized"}""");
            }
        }
        val client = testClient()

        val res = client.post("/graphql") {
            setBody(GraphQLRequest("query { number }"))
        }
        assertEquals("""{"error":"Unauthorized"}""", res.bodyAsText(), "Expected response")
    }

    @Test
    fun testGraphQLContext() = testApplication {
        testAppModule()
        routing {
            graphql {
                // Set the graphql context based on some header value
                graphQLContext(mapOf("x-req-id" to it.call.request.header("x-req-id")!!.toInt()))
            }
        }
        val client = testClient()

        val res = client.post("/graphql") {
            header("x-req-id", "1")
            setBody(GraphQLRequest("query { envConsumer }"))
        }

        assertEquals(GraphQLResponse(mapOf("envConsumer" to 1)), res.body(), "Expected response")
    }

    @Test
    fun testGraphQLContextInField() = testApplication {
        testAppModule()
        routing {
            graphql {
                // Set the graphql context based on some header value
                val secret = it.call.request.header("secret")?.toInt()
                graphQLContext(if (secret != null) mapOf("secret" to secret) else mapOf<Any, Any>())
            }
        }
        val client = testClient()

        val res = client.post("/graphql") {
            setBody(GraphQLRequest("query { restrictedInfo { restrictedField } }"))
            header("secret", "42")
        }
        assertEquals(
            GraphQLResponse(mapOf("restrictedInfo" to mapOf("restrictedField" to "sensitive info"))),
            res.body(),
            "Expected response"
        )
        val res2 = client.post("/graphql") {
            setBody(GraphQLRequest("query { restrictedInfo { restrictedField } }"))
            header("secret", "10")
        }
        assertEquals(
            GraphQLResponse(mapOf("restrictedInfo" to mapOf("restrictedField" to null))),
            res2.body(),
            "Expected response"
        )
        val res3 = client.post("/graphql") {
            setBody(GraphQLRequest("query { restrictedInfo { restrictedField } }"))
        }
        assertEquals(
            GraphQLResponse(mapOf("restrictedInfo" to mapOf("restrictedField" to null))),
            res3.body(),
            "Expected response"
        )
    }

    @Test
    fun testFetchingExceptions() = testApplication {
        application {
            install(ContentNegotiation) {
                jackson()
            }

            install(WebSockets) {
                contentConverter = JacksonWebsocketContentConverter()
            }

            install(GraphQLPlugin) {
                graphql(testSchema) {
                    //TODO defaultExceptionStatusCode(FetchingError())
                    defaultDataFetcherExceptionHandler(CustomDataFetcherExceptionHandler())
                }
            }

            routing {
                graphql("/graphql") {
                    // Set the graphql context based on some header value
                    mapOf<Any, Any>()
                }
            }
        }

        val client = testClient()

        val res = client.post("/graphql") {
            setBody(GraphQLRequest("query { throwError }"))
        }
        assertTrue(res.bodyAsText().contains("\"code\":\"${ExceptionCode.FETCHING_ERROR}\""), "Expected response")
    }
}