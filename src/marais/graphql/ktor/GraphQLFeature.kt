package marais.graphql.ktor

import io.ktor.application.*
import io.ktor.util.*

class GraphQLFeature(conf: Configuration) {


    class Configuration {

    }

    companion object Feature : ApplicationFeature<Application, Configuration, GraphQLFeature> {
        override val key = AttributeKey<GraphQLFeature>("GraphQL")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): GraphQLFeature {
            val conf = Configuration().apply(configure)

            val graphql = GraphQLFeature(conf)

            return graphql
        }
    }
}