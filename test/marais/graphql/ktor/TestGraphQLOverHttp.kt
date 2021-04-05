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

    @Test
    fun testIntrospectionQuery() = withTestApplication({
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
            val str = json.encodeToString(
                GraphQLRequest.serializer(), GraphQLRequest(
                    "query IntrospectionQuery { __schema { queryType { name } mutationType { name } subscriptionType { name } types { ...FullType } directives { name description locations args { ...InputValue          }        }      }    }    fragment FullType on __Type { kind name description fields(includeDeprecated: true) { name description args {          ...InputValue        }        type {          ...TypeRef        }        isDeprecated        deprecationReason      }      inputFields {        ...InputValue      }      interfaces {        ...TypeRef      }      enumValues(includeDeprecated: true) {        name        description        isDeprecated        deprecationReason      }      possibleTypes {        ...TypeRef      }    }    fragment InputValue on __InputValue {      name      description      type { ...TypeRef }      defaultValue    }    fragment TypeRef on __Type {      kind      name      ofType {        kind        name        ofType {          kind          name          ofType {            kind            name            ofType {              kind              name              ofType {                kind                name                ofType {                  kind                  name                  ofType {                    kind                    name                  }                }              }            }          }        }      }    }"
                )
            )
            setBody(str)
        }) {
            println(response.content)
        }
    }
}