package marais.graphql.ktor

import graphql.ExceptionWhileDataFetching
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.execution.SimpleDataFetcherExceptionHandler
import graphql.util.LogKit
import marais.graphql.ktor.data.GraphQLException
import java.lang.reflect.InvocationTargetException
import java.time.ZonedDateTime
import java.util.concurrent.CompletionException

/**
 * Custom [DataFetcherExceptionHandler] with better handling of [InvocationTargetException].
 */
class CustomDataFetcherExceptionHandler : DataFetcherExceptionHandler {

    private val logNotSafe = LogKit.getNotPrivacySafeLogger(SimpleDataFetcherExceptionHandler::class.java)

    override fun onException(handlerParameters: DataFetcherExceptionHandlerParameters): DataFetcherExceptionHandlerResult {
        val exception = unwrap(handlerParameters.exception)
        val sourceLocation = handlerParameters.sourceLocation
        val path = handlerParameters.path

        val error = if (exception is GraphQLException) { // Enriching the graphql error
            exception.asGraphQLErrorException(
                path,
                sourceLocation,
                mutableMapOf("code" to exception.code, "timestamp" to ZonedDateTime.now().toString())
            )
        } else ExceptionWhileDataFetching(path, exception, sourceLocation)
        logNotSafe.warn(error.message, exception)

        return DataFetcherExceptionHandlerResult.newResult().error(error).build()
    }

    private fun unwrap(exception: Throwable): Throwable? {
        if (exception.cause != null) {
            if (exception is CompletionException || exception is InvocationTargetException) {
                return exception.cause
            }
        }
        return exception
    }
}