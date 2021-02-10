package marais.graphql.ktor.data

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * GraphQL error representation that is spec complaint with serialization and deserialization.
 *
 * @see [GraphQL Specification](http://spec.graphql.org/June2018/#sec-Errors) for additional details
 */
@Serializable
data class GraphQLError(
    /**
     * Description of the error.
     */
    val message: String,

    /**
     * List of locations within the GraphQL document at which the exception occurred.
     */
    val locations: List<SourceLocation>? = null,

    /**
     * Path of the the response field that encountered the error.
     *
     * Path segments that represent fields should be strings, and path segments that represent list indices should be 0‐indexed integers. If the error happens in an aliased field, the path to the
     * error should use the aliased name, since it represents a path in the response, not in the query.
     */
    val path: List<@Contextual Any>? = null,

    /**
     * Additional information about the error.
     */
    val extensions: Map<String, @Serializable(with = AnyValueSerializer::class) Any?>? = null
)

/**
 * Convert the graphql-java type to the common serializable type [GraphQLError]
 */
fun graphql.GraphQLError.toGraphQLKotlinType() = GraphQLError(
    this.message.orEmpty(),
    this.locations?.map { it.toGraphQLKotlinType() },
    this.path,
    this.extensions
)
