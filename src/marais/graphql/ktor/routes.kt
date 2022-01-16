package marais.graphql.ktor

import io.ktor.application.ApplicationCall
import io.ktor.application.feature
import io.ktor.routing.*
import io.ktor.util.pipeline.ContextDsl
import io.ktor.util.pipeline.PipelineContext
import io.ktor.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.webSocket
import marais.graphql.ktor.data.Record

/**
 * The handler should return a non-null value indicating the request is accepted.
 * That value will then be used as a GraphQL execution context.
 */
@ContextDsl
fun Routing.graphql(
    path: String,
    handler: suspend PipelineContext<Unit, ApplicationCall>.() -> Map<*, Any>?
): Route {
    val gql = application.feature(GraphQLEngine)

    return route(path) {
        get {
            val context = handler(this)
            context?.let { gql.handleGet(this, it) }
        }
        post {
            val context = handler(this)
            context?.let { gql.handlePost(this, it) }
        }
    }
}

/**
 * This has no effect if allowGraphQLOverWS has been set to false.
 * The handler should return a non-null value indicating the request is accepted.
 * That value will then be used as a GraphQL execution context.
 */
@ContextDsl
fun Routing.graphqlWS(
    path: String,
    handler: suspend DefaultWebSocketServerSession.(Record?) -> Map<*, Any>?
) {
    val gql = application.feature(GraphQLEngine)

    webSocket(path, protocol = "graphql-transport-ws") {
        gql.handleWebsocket(this, handler)
    }
}
