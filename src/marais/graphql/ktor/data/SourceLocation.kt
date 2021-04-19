package marais.graphql.ktor.data

/**
 * Location describing which part of GraphQL document caused an exception.
 *
 * @see [GraphQL Specification](http://spec.graphql.org/June2018/#sec-Errors) for additional details
 */
data class SourceLocation(
    val line: Int,
    val column: Int
)

/**
 * Convert the graphql-java type to the common serializable type [SourceLocation]
 */
internal fun graphql.language.SourceLocation.toGraphQLKotlinType() = SourceLocation(
    this.line,
    this.column
)
