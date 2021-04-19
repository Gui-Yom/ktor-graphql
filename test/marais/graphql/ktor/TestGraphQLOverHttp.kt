package marais.graphql.ktor

import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import marais.graphql.ktor.data.GraphQLRequest
import marais.graphql.ktor.data.GraphQLResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class TestGraphQLOverHttp {

    @Test
    fun testSimpleQuery() = withTestApplication({
        install(GraphQLEngine) {
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
    fun testIntrospectionQuery() = withTestApplication({
        install(GraphQLEngine) {
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
}