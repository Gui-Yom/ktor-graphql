package marais.graphql.ktor

import graphql.ExecutionResult
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
import kotlinx.serialization.builtins.ArraySerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import marais.graphql.ktor.exception.KotlinGraphQLError
import marais.graphql.ktor.types.GraphQLRequest
import marais.graphql.ktor.types.GraphQLResponse
import marais.graphql.ktor.types.ID
import marais.graphql.ktor.types.Message
import org.reactivestreams.Publisher

class GraphQLEngine(conf: Configuration) {

    val allowGraphQLOverWS = conf.allowGraphQLOverWS
    val json = conf.json
    val graphql = GraphQL.Builder(null).apply(conf.graphqlConfiguration).build()

    suspend fun handleGet(ctx: PipelineContext<Unit, ApplicationCall>) {
        // TODO handle get method
    }

    suspend fun handlePost(ctx: PipelineContext<Unit, ApplicationCall>) {
        val jsonElem = json.decodeFromString(JsonElement.serializer(), ctx.call.receiveText())
        try {
            val batch = json.decodeFromJsonElement(ArraySerializer(GraphQLRequest.serializer()), jsonElem)
                .map { handleGraphQL(it) }
            ctx.call.respond(batch)
        } catch (e: Exception) { // We couldn't parse it as a batched query.
            // TODO it might be faster to first serialize it as a JsonElement then make it coerce with each one.
            val single = handleGraphQL(json.decodeFromJsonElement(GraphQLRequest.serializer(), jsonElem))
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

        val subscriptions = mutableMapOf<ID, Publisher<ExecutionResult>>()

        // Ready to process
        for (frame in ws.incoming) {
            when (frame) {
                is Frame.Text -> {
                    val msg = json.decodeFromString(Message.serializer(), frame.readText())
                    when (msg) {
                        is Message.Subscribe -> {
                            val result = graphql.executeAsync(msg.payload.toExecutionInput(null, null)).await()
                            // If what's inside the execution result is a publisher, then its a running subscription
                            if (result.isSubscription()) {
                                subscriptions[msg.id] = result.getData()
                                // TODO handle new subscription
                            } else { // Its a normal query/mutation
                                ws.send(
                                    json.encodeToString(
                                        Message.serializer(),
                                        Message.Next(msg.id, result.toGraphQLResponse())
                                    )
                                )
                                ws.send(json.encodeToString(Message.serializer(), Message.Complete(msg.id)))
                            }
                        }
                        is Message.Complete -> {
                            subscriptions.remove(msg.id)
                        }
                        else -> {
                            // TODO wrong message
                        }
                    }
                }
                else -> {

                }
            }
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

        fun graphqlConfig(config: GraphQL.Builder.() -> Unit) {
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
