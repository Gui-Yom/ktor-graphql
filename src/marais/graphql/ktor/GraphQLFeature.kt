package marais.graphql.ktor

import com.expediagroup.graphql.generator.SchemaGenerator
import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelNames
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.execution.SimpleKotlinDataFetcherFactoryProvider
import com.expediagroup.graphql.generator.hooks.NoopSchemaGeneratorHooks
import graphql.GraphQL
import io.ktor.application.*
import io.ktor.routing.*
import io.ktor.util.*
import kotlin.reflect.KType

class GraphQLFeature(conf: Configuration) {

    val graphql = GraphQL.newGraphQL(conf.buildSchema())

    class Configuration {
        var queries = emptyList<TopLevelObject>()
        var mutations = emptyList<TopLevelObject>()
        var subscriptions = emptyList<TopLevelObject>()
        var additionalTypes = emptySet<KType>()
        var additionalInputTypes = emptySet<KType>()
        var supportedPackages = emptyList<String>()
        var hooks = NoopSchemaGeneratorHooks
        var introspectionEnabled = true

        internal fun buildSchema() = SchemaGenerator(
            SchemaGeneratorConfig(
                supportedPackages,
                TopLevelNames(),
                hooks,
                SimpleKotlinDataFetcherFactoryProvider(),
                introspectionEnabled
            )
        ).generateSchema(
            queries,
            mutations,
            subscriptions,
            additionalTypes,
            additionalInputTypes
        )
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

fun Route.graphql(path: String): Route {
    return post(path) {

    }
}
