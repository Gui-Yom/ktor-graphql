package marais.graphql.ktor

import graphql.ExecutionInput
import graphql.ExecutionResult
import io.ktor.server.application.plugin
import io.ktor.server.routing.Routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.util.KtorDsl
import io.ktor.websocket.close
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.collect
import kotlinx.coroutines.withTimeoutOrNull
import marais.graphql.ktor.data.CloseReasons
import marais.graphql.ktor.data.ID
import marais.graphql.ktor.data.Message
import marais.graphql.ktor.data.Record
import marais.graphql.ktor.data.isFlow
import marais.graphql.ktor.data.isPublisher
import marais.graphql.ktor.data.toGraphQLResponse
import org.reactivestreams.Publisher
import kotlin.collections.contains
import kotlin.collections.emptyMap
import kotlin.collections.forEach
import kotlin.collections.mutableMapOf
import kotlin.collections.set

/**
 * @param path the path to listen on for websocket connections
 * @param onConnection called for each new connection, should return a lambda configuring the execution input for each request
 */
@KtorDsl
fun Routing.graphqlWS(
    path: String = "/graphql",
    onConnection: suspend DefaultWebSocketServerSession.(Record?) -> (suspend ExecutionInput.Builder.() -> Unit) = { {} },
) {
    val gql = application.plugin(GraphQLPlugin)

    webSocket(path, protocol = "graphql-transport-ws") {
        // Receive connection init message
        // TODO configurable initial timeout
        val msg = withTimeoutOrNull(5000) {
            receiveDeserialized<Message>()
        }
        if (msg == null) {
            close(CloseReasons.INIT_TIMEOUT)
        } else {
            when (msg) {
                is Message.ConnectionInit -> {
                    val builder = onConnection(msg.payload)
                    sendSerialized<Message>(Message.ConnectionAck(emptyMap()))
                    handleSession(gql, builder)
                }

                else -> {
                    close(CloseReasons.UNAUTHORIZED)
                }
            }
        }
    }
}

private suspend fun DefaultWebSocketServerSession.handleSession(
    gql: GraphQLPlugin,
    builder: suspend ExecutionInput.Builder.() -> Unit
) {
    // This is scoped to a single connection
    val subscriptions = mutableMapOf<ID, Job>()

    // Scope to launch in
    coroutineScope {
        // Ready to process
        try {
            while (true) {
                when (val msg = receiveDeserialized<Message>()) {
                    // Client wants to start a new subscription
                    is Message.Subscribe -> {

                        if (msg.id in subscriptions) {
                            close(CloseReasons.ALREADY_SUBSCRIBED(msg.id))
                            return@coroutineScope
                        }

                        val input = msg.payload.toExecutionInput()
                        builder(input)
                        val result = gql.graphql.executeAsync(input.build()).await()

                        // If what's inside the execution result is a publisher, then its a running subscription
                        if (result.isPublisher()) {
                            val pub = result.getData<Publisher<ExecutionResult>>()
                            subscriptions[msg.id] = launch {
                                // Internally, this creates a Channel and other things
                                // Returning a flow in a subscription is definitely more performant
                                pub.collect {
                                    sendSerialized<Message>(Message.Next(msg.id, it.toGraphQLResponse()))
                                }
                                sendSerialized<Message>(Message.Complete(msg.id))
                                subscriptions.remove(msg.id)
                            }

                            // We also check if it's a flow
                        } else if (result.isFlow()) {
                            val flow = result.getData<Flow<ExecutionResult>>()
                            subscriptions[msg.id] = launch {
                                flow.collect {
                                    sendSerialized<Message>(Message.Next(msg.id, it.toGraphQLResponse()))
                                }
                                sendSerialized<Message>(Message.Complete(msg.id))
                                subscriptions.remove(msg.id)
                            }
                        } else { // It's a normal query/mutation
                            sendSerialized<Message>(Message.Next(msg.id, result.toGraphQLResponse()))
                            sendSerialized<Message>(Message.Complete(msg.id))
                        }
                    }
                    // Client wants to terminate a subscription
                    is Message.Complete -> {
                        subscriptions.remove(msg.id)?.cancel()
                    }
                    // Client wants to init a graphql-ws connection but the connection is already opened
                    is Message.ConnectionInit -> {
                        close(CloseReasons.ALREADY_INIT)
                        return@coroutineScope
                    }

                    is Message.Ping -> {
                        sendSerialized<Message>(Message.Pong())
                    }

                    is Message.Pong -> {
                        // Nothing
                    }
                    // Client sent us an unexpected message
                    else -> {
                        close(CloseReasons.UNEXPECTED_MESSAGE)
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
            gql.log.warn(e.message)
            e.printStackTrace()
        } finally {
            subscriptions.values.apply {
                forEach(Job::cancel)
                joinAll()
            }
        }
    }
}
