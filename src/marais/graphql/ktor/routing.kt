package marais.graphql.ktor

import graphql.ExecutionInput
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

/**
 * The handler should return a non-null value indicating the request is accepted.
 * That value will then be used as a GraphQL execution context.
 */
@KtorDsl
fun Routing.graphql(
    path: String = "/graphql",
    builder: suspend ExecutionInput.Builder.(PipelineContext<Unit, ApplicationCall>) -> Unit = {}
): Route {
    val gql = application.plugin(GraphQLPlugin)

    return route(path) {
        get {
            gql.handleGet(this) { builder(this@get) }
        }
        post {
            gql.handlePost(this) { builder(this@post) }
        }
    }
}
