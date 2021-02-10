package marais.graphql.ktor.data

import graphql.ErrorClassification
import graphql.ErrorType
import graphql.GraphQLError
import graphql.language.SourceLocation

/**
 * Generic implementation of [GraphQLError].
 */
open class KotlinGraphQLError(
    private val exception: Throwable,
    private val locations: List<SourceLocation>? = null,
    private val path: List<Any>? = null,
    private val errorType: ErrorClassification = ErrorType.DataFetchingException
) : GraphQLError {
    override fun getErrorType(): ErrorClassification = errorType

    override fun getExtensions(): Map<String, Any> =
        if (exception is GraphQLError && exception.extensions != null) {
            exception.extensions
        } else {
            emptyMap()
        }

    override fun getLocations(): List<SourceLocation>? = locations

    override fun getMessage(): String =
        "Exception while fetching data (${path?.joinToString("/").orEmpty()}) : ${exception.message}"

    override fun getPath(): List<Any>? = path
}
