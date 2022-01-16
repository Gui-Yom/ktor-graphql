package marais.graphql.ktor.data

/**
 * GraphQL Error representation that is spec complaint with serialization and deserialization.
 *
 * @see [GraphQL Specification](http://spec.graphql.org/June2018/#sec-Errors) for additional details
 */
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
     * Path of the response field that encountered the error.
     *
     * Path segments that represent fields should be strings, and path segments that represent list indices should be 0‐indexed integers. If the error happens in an aliased field, the path to the
     * error should use the aliased name, since it represents a path in the response, not in the query.
     */
    val path: List<Any?>? = null,

    /**
     * Additional information about the error.
     */
    val extensions: Map<String, Any?>? = null
)

fun Throwable.toGraphQlError(): GraphQLError {
    return if (this is graphql.GraphQLError) (this as graphql.GraphQLError).toGraphQLKotlinType()
    else {
        // This case should never happen with a correct DataFetcherExceptionHandlerResult wrapping non-standard errors
        GraphQLError(message ?: "An unknown error occurred", null, null, null)
    }
}

fun graphql.GraphQLError.toGraphQLKotlinType(): GraphQLError {
    return GraphQLError(message, locations.map { it.toGraphQLKotlinType() }, path, extensions)
}