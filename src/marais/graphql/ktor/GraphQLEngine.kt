package marais.graphql.ktor

import graphql.ExecutionResult
import graphql.GraphQL
import graphql.schema.GraphQLSchema
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.websocket.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.serialization.builtins.ArraySerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import marais.graphql.ktor.data.*
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory

class GraphQLEngine(conf: Configuration) {

    val allowGraphQLOverWS = conf.allowGraphQLOverWS
    private val json = conf.json
    val graphqlSchema = conf.schema
    val graphql = GraphQL.Builder(conf.schema).apply(conf.graphqlConfiguration).build()
    private val log = LoggerFactory.getLogger(GraphQLEngine::class.java)

    suspend fun handleGet(ctx: PipelineContext<Unit, ApplicationCall>, unit: Unit) {
        val query = ctx.call.request.queryParameters["query"]
        if (query == null) {
            ctx.call.respond(HttpStatusCode.BadRequest, "Missing 'query' parameter")
            return
        }
        val variables = ctx.call.request.queryParameters["variables"]?.let {
            json.decodeFromString(
                MapSerializer(
                    String.serializer(),
                    AnyValueSerializer
                ),
                it
            )
        }
        ctx.call.respond(
            handleGraphQL(
                GraphQLRequest(
                    query,
                    ctx.call.request.queryParameters["operationName"],
                    variables
                )
            )
        )
    }

    suspend fun handlePost(ctx: PipelineContext<Unit, ApplicationCall>, unit: Unit) {
        val jsonElem = json.decodeFromString(JsonElement.serializer(), ctx.call.receiveText())
        try {
            val batch = json.decodeFromJsonElement(ArraySerializer(GraphQLRequest.serializer()), jsonElem)
                .map { handleGraphQL(it) }
            ctx.call.respond(batch)
        } catch (e: Exception) { // We couldn't parse it as a batched query.
            val single = handleGraphQL(json.decodeFromJsonElement(GraphQLRequest.serializer(), jsonElem))
            ctx.call.respond(single)
        }
    }

    suspend fun handleGraphQL(input: GraphQLRequest): GraphQLResponse {
        return try {
            // TODO graphql context and dataloader registry
            graphql.executeAsync(input.toExecutionInput(null, null)).await().toGraphQLResponse()
        } catch (exception: Exception) {
            val graphKotlinQLError = KotlinGraphQLError(exception)
            GraphQLResponse(errors = listOf(graphKotlinQLError.toGraphQLKotlinType()))
        }
    }

    suspend fun handleWebsocket(ws: DefaultWebSocketServerSession) {
        // Receive connection init message
        // TODO wait timeout
        when (val msg = ws.incoming.receive().toMessage(json)) {
            is Message.ConnectionInit -> {
                // TODO maybe allow to do something with [msg.payload]
                ws.sendMessage(json, Message.ConnectionAck(emptyMap()))
            }
            else -> {
                ws.close(CloseReason(4401, "Unauthorized"))
                return
            }
        }

        val subscriptions = mutableMapOf<ID, Job>()

        // Scope to launch in
        coroutineScope {
            // Ready to process
            try {
                for (frame in ws.incoming) {
                    when (val msg = frame.toMessage(json)) {
                        is Message.Subscribe -> {

                            if (msg.id in subscriptions) {
                                ws.close(CloseReason(4409, "Subscriber for <unique-operation-id> already exists"))
                                return@coroutineScope
                            }

                            // TODO graphql context and dataloader registry
                            val result = graphql.executeAsync(msg.payload.toExecutionInput(null, null)).await()
                            // If what's inside the execution result is a publisher, then its a running subscription
                            if (result.isSubscription()) {
                                // Non blocking, so we can continue processing of other requests while streaming this subscription
                                val pub = result.getData<Publisher<ExecutionResult>>()
                                    .asFlow()
                                    .buffer(1)
                                subscriptions[msg.id] = launch {
                                    pub.collect {
                                        ws.sendMessage(json, Message.Next(msg.id, it.toGraphQLResponse()))
                                    }
                                    ws.sendMessage(json, Message.Complete(msg.id))
                                    subscriptions.remove(msg.id)
                                }

                            } else { // Its a normal query/mutation
                                ws.sendMessage(json, Message.Next(msg.id, result.toGraphQLResponse()))
                                ws.sendMessage(json, Message.Complete(msg.id))
                            }
                        }
                        is Message.Complete -> {
                            subscriptions.remove(msg.id)?.cancel()
                        }
                        is Message.ConnectionInit -> {
                            ws.close(CloseReason(4429, "Too many initialisation requests"))
                            return@coroutineScope
                        }
                        else -> {
                            ws.close(CloseReason(4400, "Unexpected message"))
                            return@coroutineScope
                        }
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                println("onClose ${ws.closeReason.await()}")
            } catch (e: Throwable) {
                println("onError ${ws.closeReason.await()}")
                e.printStackTrace()
            }
        }
    }

    class Configuration {
        /**
         * Allow graphql-over-websocket communication, requires ktor `Websockets` feature to be installed.
         */
        var allowGraphQLOverWS = false
        var json: Json = Json

        /**
         * It is prefered to specify the schema with this property instead of directly in the builder so we can access it later
         */
        var schema: GraphQLSchema? = null
        internal var graphqlConfiguration: GraphQL.Builder.() -> Unit = {}

        fun builder(config: GraphQL.Builder.() -> Unit) {
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
