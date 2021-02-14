package marais.graphql.ktor

import io.ktor.application.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import io.ktor.websocket.*

@ContextDsl
fun Routing.graphql(
    path: String,
    handler: suspend PipelineContext<Unit, ApplicationCall>.() -> Unit
): Route {
    val gql = application.feature(GraphQLEngine)

    return route(path) {

        handle { handler(this) }

        if (gql.allowGraphQLOverWS) {
            webSocket(protocol = "graphql-transport-ws") { gql.handleWebsocket(this) }
        }

        get { gql.handleGet(this) }
        post { gql.handlePost(this) }
    }
}
