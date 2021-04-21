package marais.graphql.ktor

import io.ktor.application.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import io.ktor.websocket.*

@ContextDsl
fun Routing.graphql(
    path: String,
    handler: suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit
): Route {
    val gql = application.feature(GraphQLEngine)

    return route(path) {

        // FIXME this handler is not behaving correctly
        handle(handler)

        if (gql.allowGraphQLOverWS) {
            webSocket(protocol = "graphql-transport-ws", gql::handleWebsocket)
        }

        get(gql::handleGet)
        post(gql::handlePost)
    }
}
