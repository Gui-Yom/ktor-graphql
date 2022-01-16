package marais.graphql.ktor.data

import graphql.ErrorClassification
import graphql.GraphqlErrorException
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.ResultPath
import graphql.language.SourceLocation

/**
 * Generic implementation of [RuntimeException] that can easily be supported by a custom [DataFetcherExceptionHandler]
 * to be enriched with [asGraphQLErrorException] (i.e. producing a standard [GraphqlErrorException]).
 */
open class GraphQLException(
    message: String?,
    val code: ErrorClassification,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    open fun asGraphQLErrorException(
        path: ResultPath,
        sourceLocation: SourceLocation,
        extensions: MutableMap<String, Any>
    ): GraphqlErrorException {
        return GraphqlErrorException.newErrorException()
            .message(message)
            .cause(cause)
            .errorClassification(code)
            .extensions(extensions as Map<String, Any>)
            .path(path.toList())
            .sourceLocation(sourceLocation)
            .build()
    }
}