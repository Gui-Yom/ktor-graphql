package marais.graphql.ktor

import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json

class GraphQLWS(private val conf: Configuration) {

    private fun install(application: Application) {
        application.routing {
            webSocket(conf.path, "graphql-transport-ws") {

                // Receive connection init message
                // TODO wait timeout
                when (val frame = incoming.receive()) {
                    is Frame.Text -> {
                        val msg = Json.decodeFromString(Message.serializer(), frame.readText())
                        when (msg) {
                            is Message.ConnectionInit -> {
                                send(Json.encodeToString(Message.serializer(), Message.ConnectionAck(msg.payload)))
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
                for (frame in incoming) {

                }
            }
        }
    }

    class Configuration {
        var path = "/graphql"
    }

    companion object Feature : ApplicationFeature<Application, Configuration, GraphQLWS> {

        override val key = AttributeKey<GraphQLWS>("GraphQLWS")
        override fun install(pipeline: Application, configure: Configuration.() -> Unit): GraphQLWS {

            pipeline.feature(WebSockets)
            pipeline.feature(GraphQLFeature)

            val conf = Configuration().apply(configure)
            val feature = GraphQLWS(conf)

            feature.install(pipeline)

            return feature
        }

    }
}