package marais.graphql.ktor

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.jackson.JacksonWebsocketContentConverter
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.websocket.WebSockets

val MAPPER: ObjectMapper = ObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .registerModule(
        KotlinModule.Builder()
            .withReflectionCacheSize(512)
            .configure(KotlinFeature.NullToEmptyCollection, false)
            .configure(KotlinFeature.NullToEmptyMap, false)
            .configure(KotlinFeature.NullIsSameAsDefault, false)
            .configure(KotlinFeature.SingletonSupport, false)
            .configure(KotlinFeature.StrictNullChecks, false).build()
    )

fun ApplicationTestBuilder.testAppModule() {
    environment {
        developmentMode = false
    }

    application {
        install(ContentNegotiation) {
            jackson()
        }

        install(WebSockets) {
            contentConverter = JacksonWebsocketContentConverter()
        }

        install(GraphQLPlugin) {
            mapper = MAPPER
            graphql(testSchema)
        }
    }
}

fun ApplicationTestBuilder.testClient() = createClient {
    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
        jackson()
    }

    install(io.ktor.client.plugins.websocket.WebSockets) {
        contentConverter = JacksonWebsocketContentConverter()
    }

    defaultRequest {
        contentType(ContentType.Application.Json)
        accept(ContentType.Application.Json)
    }
}

suspend fun ApplicationTestBuilder.graphqlWSSession(
    path: String = "/graphql",
    handler: suspend DefaultClientWebSocketSession.() -> Unit
) {
    testClient().webSocket(path, { header(HttpHeaders.SecWebSocketProtocol, "graphql-transport-ws") }, handler)
}
