package marais.graphql.ktor

import com.fasterxml.jackson.databind.DeserializationFeature
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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.collect
import marais.graphql.ktor.data.*
import org.apache.logging.log4j.LogManager
import org.dataloader.DataLoaderRegistry
import org.reactivestreams.Publisher
import kotlin.collections.set

class GraphQLEngine(conf: Configuration) {

    private val mapper = conf.mapper
    val graphqlSchema = conf.schema
    val graphql = GraphQL.Builder(conf.schema).apply(conf.graphqlConfiguration).build()

    private val dataLoaderRegistry = DataLoaderRegistry()

    private val log = LogManager.getLogger()

    suspend fun handleGet(ctx: PipelineContext<Unit, ApplicationCall>, gqlCtx: Any) {
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
                ),
                gqlCtx
            )
        )
    }

    suspend fun handlePost(ctx: PipelineContext<Unit, ApplicationCall>, gqlCtx: Any) {
        val json = ctx.call.receiveText()
        try {
            val request = mapper.readValue(json, GraphQLRequest::class.java)
            ctx.call.respond(handleGraphQL(request, gqlCtx))

            // IMHO That is one terrible way of handling the case where we get an array of queries
            // But hey it works
        } catch (e: JsonMappingException) {
            val batch: Array<GraphQLRequest> = mapper.readerForArrayOf(GraphQLRequest::class.java).readValue(json)
            // Run all queries concurrently
            ctx.call.respond(coroutineScope {
                batch.map { async { handleGraphQL(it, gqlCtx) } }
            }.awaitAll())
        } catch (e: Exception) {
            // TODO meaningful messages
            // TODO json error messages
            ctx.call.respond(HttpStatusCode.BadRequest, "Bad request")
        }
    }

    suspend fun handleGraphQL(input: GraphQLRequest, context: Any): GraphQLResponse {
        return try {
            graphql.executeAsync(input.toExecutionInput(context, dataLoaderRegistry)).await().toGraphQLResponse()
        } catch (exception: Exception) {
            val graphKotlinQLError = KotlinGraphQLError(exception)
            GraphQLResponse(errors = listOf(graphKotlinQLError.toGraphQLKotlinType()))
        }
    }

    suspend fun handleWebsocket(
        ws: DefaultWebSocketServerSession,
        contextBuilder: suspend DefaultWebSocketServerSession.(Record?) -> Any?
    ) {
        var context: Any? = null

        // Receive connection init message
        // TODO configurable initial timeout
        val msg = withTimeoutOrNull(5000) {
            ws.receiveMessage(mapper)
        }
        if (msg == null) {
            ws.close(CloseReasons.INIT_TIMEOUT)
            return
        }
        when (msg) {
            is Message.ConnectionInit -> {
                context = contextBuilder(ws, msg.payload)
                ws.sendMessage(mapper, Message.ConnectionAck(emptyMap()))
            }
            else -> {
                ws.close(CloseReasons.UNAUTHORIZED)
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
                        // Client wants to start a new subscription
                        is Message.Subscribe -> {

                            if (msg.id in subscriptions) {
                                ws.close(CloseReasons.ALREADY_SUBSCRIBED(msg.id))
                                return@coroutineScope
                            }

                            val result = graphql.executeAsync(
                                msg.payload.toExecutionInput(
                                    context,
                                    dataLoaderRegistry
                                )
                            ).await()

                            // If what's inside the execution result is a publisher, then its a running subscription
                            if (result.isPublisher()) {
                                val pub = result.getData<Publisher<ExecutionResult>>()
                                subscriptions[msg.id] = launch {
                                    // Internally, this creates a Channel and other things
                                    // Returning a flow in a subscription is definitely more performant
                                    pub.collect {
                                        ws.sendMessage(mapper, Message.Next(msg.id, it.toGraphQLResponse()))
                                    }
                                    ws.sendMessage(mapper, Message.Complete(msg.id))
                                    subscriptions.remove(msg.id)
                                }

                                // We also check if it's a flow
                            } else if (result.isFlow()) {
                                val flow = result.getData<Flow<ExecutionResult>>()
                                subscriptions[msg.id] = launch {
                                    flow.collect {
                                        ws.sendMessage(mapper, Message.Next(msg.id, it.toGraphQLResponse()))
                                    }
                                    ws.sendMessage(mapper, Message.Complete(msg.id))
                                    subscriptions.remove(msg.id)
                                }
                            } else { // It's a normal query/mutation
                                ws.sendMessage(mapper, Message.Next(msg.id, result.toGraphQLResponse()))
                                ws.sendMessage(mapper, Message.Complete(msg.id))
                            }
                        }
                        // Client wants to terminate a subscription
                        is Message.Complete -> {
                            subscriptions.remove(msg.id)?.cancel()
                        }
                        // Client wants to init a graphql-ws connection but the connection is already opened
                        is Message.ConnectionInit -> {
                            ws.close(CloseReasons.ALREADY_INIT)
                            return@coroutineScope
                        }
                        is Message.Ping -> {
                            ws.sendMessage(mapper, Message.Pong())
                        }
                        is Message.Pong -> {
                            // Nothing
                        }
                        // Client sent us an unexpected message
                        else -> {
                            ws.close(CloseReasons.UNEXPECTED_MESSAGE)
                            return@coroutineScope
                        }
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                // Closed more or less gracefully
            } catch (e: CancellationException) {
                // MMMh
            } catch (e: Throwable) {
                // Closed much less gracefully
                log.warn(e.message)
                e.printStackTrace()
            } finally {
                subscriptions.values.apply {
                    forEach(Job::cancel)
                    joinAll()
                }
            }
        }
    }

    class Configuration {
        var mapper: ObjectMapper = ObjectMapper()
            .registerModule(KotlinModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

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

            val graphql = GraphQLEngine(conf)
            return graphql
        }
    }
}
