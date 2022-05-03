package marais.graphql.ktor

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.WebSockets
import io.ktor.util.*

val mapper: ObjectMapper = ObjectMapper()
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

fun ApplicationTestBuilder.testAppModule() = application {
    install(ContentNegotiation) {
        jackson()
    }

    install(WebSockets) {
        contentConverter = JacksonContentConverter(mapper)
    }

    install(GraphQLPlugin) {
        graphql(testSchema)
    }
}

fun ApplicationTestBuilder.testClient() = createClient {
    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
        jackson()
    }

    install(io.ktor.client.plugins.websocket.WebSockets) {
        contentConverter = JacksonContentConverter(mapper)
    }

    defaultRequest {
        headers.appendIfNameAbsent(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
}

suspend fun ApplicationTestBuilder.graphqlWSSession(
    path: String = "/graphql",
    handler: suspend DefaultClientWebSocketSession.() -> Unit
) {
    testClient().webSocket(path, { header(HttpHeaders.SecWebSocketProtocol, "graphql-transport-ws") }, handler)
}
