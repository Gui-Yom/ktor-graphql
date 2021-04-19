package marais.graphql.ktor

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.schema.GraphQLSchema
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.request.*
import io.ktor.response.*
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
import marais.graphql.ktor.data.*
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import kotlin.collections.set

class GraphQLEngine(conf: Configuration) {

    val allowGraphQLOverWS = conf.allowGraphQLOverWS
    private val mapper = conf.mapper
    val graphqlSchema = conf.schema
    val graphql = GraphQL.Builder(conf.schema).apply(conf.graphqlConfiguration).build()
    private val log = LoggerFactory.getLogger(GraphQLEngine::class.java)

    suspend fun handleGet(ctx: PipelineContext<Unit, ApplicationCall>, unit: Unit) {
        val query = ctx.call.request.queryParameters["query"]
        if (query == null) {
            ctx.call.respond(HttpStatusCode.BadRequest, "Missing 'query' parameter")
            return
        }
        val variables: Map<String, Any?>? = ctx.call.request.queryParameters["variables"]?.let {
            mapper.readerForMapOf(Any::class.java).readValue(it)
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
        val json = ctx.call.receiveText()
        try {
            val request = mapper.readValue(json, GraphQLRequest::class.java)
            ctx.call.respond(handleGraphQL(request))
        } catch (e: JsonMappingException) {
            error(e)
            val batch: Array<GraphQLRequest> = mapper.readerForArrayOf(GraphQLRequest::class.java).readValue(json)
            ctx.call.respond(batch.map { handleGraphQL(it) })
        } catch (e: Exception) {
            // TODO meaningful messages
            // TODO json error messages
            ctx.call.respond(HttpStatusCode.BadRequest, "Bad request")
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
        when (val msg = ws.receiveMessage(mapper)) {
            is Message.ConnectionInit -> {
                // TODO maybe allow to do something with [msg.payload]
                ws.sendMessage(mapper, Message.ConnectionAck(emptyMap()))
            }
            else -> {
                ws.close(CloseReason(4401, "Unauthorized"))
                return
            }
        }

        // This is scoped to a single connection
        val subscriptions = mutableMapOf<ID, Job>()

        // Scope to launch in
        coroutineScope {
            // Ready to process
            try {
                for (frame in ws.incoming) {
                    when (val msg = frame.toMessage(mapper)) {
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
                                        ws.sendMessage(mapper, Message.Next(msg.id, it.toGraphQLResponse()))
                                    }
                                    ws.sendMessage(mapper, Message.Complete(msg.id))
                                    subscriptions.remove(msg.id)
                                }

                            } else { // Its a normal query/mutation
                                ws.sendMessage(mapper, Message.Next(msg.id, result.toGraphQLResponse()))
                                ws.sendMessage(mapper, Message.Complete(msg.id))
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

        var mapper = ObjectMapper().registerModule(KotlinModule())

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
