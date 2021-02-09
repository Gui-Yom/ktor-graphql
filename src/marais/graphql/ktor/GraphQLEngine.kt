package marais.graphql.ktor

import graphql.GraphQL
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.websocket.*
import kotlinx.coroutines.future.await
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import marais.graphql.ktor.exception.KotlinGraphQLError
import marais.graphql.ktor.types.GraphQLRequest
import marais.graphql.ktor.types.GraphQLResponse
import marais.graphql.ktor.types.Message

class GraphQLEngine(conf: Configuration) {

    val allowGraphQLOverWS = conf.allowGraphQLOverWS
    val json = conf.json
    val graphql = GraphQL.Builder(null).apply(conf.graphqlConfiguration).build()

    suspend fun handleGet(ctx: PipelineContext<Unit, ApplicationCall>) {

    }

    suspend fun handlePost(ctx: PipelineContext<Unit, ApplicationCall>) {
        val text = ctx.call.receiveText()
        try {
            val batch = json.decodeFromString<Array<GraphQLRequest>>(text).map { handleGraphQL(it) }
            ctx.call.respond(batch)
        } catch (e: Exception) { // We couldn't parse it as a batched query.
            // TODO it might be faster to first serialize it as a JsonElement then make it coerce with each one.
            val single = handleGraphQL(json.decodeFromString(GraphQLRequest.serializer(), text))
            ctx.call.respond(single)
        }
    }

    suspend fun handleWebsocket(ws: DefaultWebSocketServerSession) {
        // Receive connection init message
        // TODO wait timeout
        when (val frame = ws.incoming.receive()) {
            is Frame.Text -> {
                val msg = json.decodeFromString(Message.serializer(), frame.readText())
                when (msg) {
                    is Message.ConnectionInit -> {
                        ws.send(json.encodeToString(Message.serializer(), Message.ConnectionAck(msg.payload)))
                    }
                    else -> {
                        // TODO wrong message
                    }
                }
            }
            else -> {
                // TODO wrong frame
            }
        }

        // Ready to process
        for (frame in ws.incoming) {

        }
    }

    suspend fun handleGraphQL(input: GraphQLRequest): GraphQLResponse {
        return try {
            graphql.executeAsync(input.toExecutionInput(null, null)).await().toGraphQLResponse()
        } catch (exception: Exception) {
            val graphKotlinQLError = KotlinGraphQLError(exception)
            GraphQLResponse(errors = listOf(graphKotlinQLError.toGraphQLKotlinType()))
        }
    }

    class Configuration {
        /**
         * Allow graphql-over-websocket communication, requires ktor `Websockets` feature to be installed.
         */
        var allowGraphQLOverWS = false
        var json = Json
        internal var graphqlConfiguration: GraphQL.Builder.() -> Unit = {}

        infix fun graphqlConfig(config: GraphQL.Builder.() -> Unit) {
            this.graphqlConfiguration = config
        }
    }

    companion object Feature : ApplicationFeature<Application, Configuration, GraphQLEngine> {
        override val key = AttributeKey<GraphQLEngine>("GraphQL")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): GraphQLEngine {
            val conf = Configuration().apply(configure)

            if (conf.allowGraphQLOverWS) {
                // The websocket feature is required
                pipeline.feature(WebSockets)
            }

            val graphql = GraphQLEngine(conf)
            return graphql
        }
    }
}

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
