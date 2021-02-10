package marais.graphql.ktor.data

import graphql.ExecutionResult
import kotlinx.serialization.Serializable
import org.reactivestreams.Publisher

/**
 * GraphQL response that is spec complaint with serialization and deserialization.
 *
 * @see [GraphQL Specification](http://spec.graphql.org/June2018/#sec-Data) for additional details
 */
@Serializable
data class GraphQLResponse(
    val data: Map<String, @Serializable(with = AnyValueSerializer::class) Any?>? = null,
    val errors: List<GraphQLError>? = null,
    val extensions: Map<String, @Serializable(with = AnyValueSerializer::class) Any?>? = null
)

/**
 * Convert a graphql-java result to the common serializable type [GraphQLResponse]
 */
fun ExecutionResult.toGraphQLResponse(): GraphQLResponse {
    val data = getData<Map<String, Any?>?>()
    val filteredErrors: List<GraphQLError>? =
        if (errors?.isNotEmpty() == true) errors?.map { it.toGraphQLKotlinType() } else null
    val filteredExtensions: MutableMap<Any, Any>? = if (extensions?.isNotEmpty() == true) extensions else null
    return GraphQLResponse(data, filteredErrors, filteredExtensions as Map<String, Any?>?)
}

fun ExecutionResult.isSubscription() = getData<Any?>() is Publisher<*>

