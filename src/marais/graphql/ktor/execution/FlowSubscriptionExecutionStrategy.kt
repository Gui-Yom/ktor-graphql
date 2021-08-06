package marais.graphql.ktor.execution

import graphql.AssertException
import graphql.ExecutionResult
import graphql.ExecutionResultImpl
import graphql.execution.*
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters
import graphql.execution.reactive.SubscriptionPublisher
import graphql.schema.GraphQLObjectType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.future.await
import org.reactivestreams.Publisher
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * [SubscriptionExecutionStrategy] only accepts [org.reactivestreams.Publisher] as fetcher return type.
 * This implementation allows using [Flow] and [org.reactivestreams.Publisher] as return type.
 *
 * Code from this class is copied from [SubscriptionExecutionStrategy] and converted to kotlin by IntelliJ, then cleaned by hand.
 * Changes are only in the function [execute]. [SubscriptionExecutionStrategy.createSourceEventStream] has been inlined in [execute].
 */
class FlowSubscriptionExecutionStrategy(dataFetcherExceptionHandler: DataFetcherExceptionHandler) :
    ExecutionStrategy(dataFetcherExceptionHandler) {

    constructor() : this(SimpleDataFetcherExceptionHandler())

    override fun execute(
        executionContext: ExecutionContext,
        parameters: ExecutionStrategyParameters
    ): CompletableFuture<ExecutionResult>? {
        val instrumentation = executionContext.instrumentation
        val instrumentationParameters = InstrumentationExecutionStrategyParameters(executionContext, parameters)
        val executionStrategyCtx = instrumentation.beginExecutionStrategy(instrumentationParameters)

        val newParameters = firstFieldOfSubscriptionSelection(parameters)
        val fieldFetched = fetchField(executionContext, newParameters)
        val sourceFut = fieldFetched.thenApply(FetchedValue::getFetchedValue)

        // when the upstream source event stream completes, subscribe to it and wire in our adapter
        val overallResult = sourceFut.thenApply<ExecutionResult> { source ->
            if (source == null) {
                return@thenApply ExecutionResultImpl(null, executionContext.errors)
            }

            val transformed: Any = when (source) {
                is Flow<*> -> source.map { executeSubscriptionEvent(executionContext, parameters, it).await() }
                is Publisher<*> -> {
                    SubscriptionPublisher(source as Publisher<Any?>?) {
                        executeSubscriptionEvent(
                            executionContext,
                            parameters,
                            it
                        )
                    }
                }
                else -> throw AssertException(
                    """
                    This subscription execution strategy support the following return types for your subscriptions fetchers :
                    - org.reactivestreams.Publisher
                    - kotlinx.coroutines.Flow
                """.trimIndent()
                )
            }

            ExecutionResultImpl(transformed, executionContext.errors)
        }

        // dispatched the subscription query
        executionStrategyCtx.onDispatched(overallResult)
        overallResult.whenComplete { result: ExecutionResult, t: Throwable? ->
            executionStrategyCtx.onCompleted(
                result,
                t
            )
        }
        return overallResult
    }

    private fun executeSubscriptionEvent(
        executionContext: ExecutionContext,
        parameters: ExecutionStrategyParameters,
        eventPayload: Any?
    ): CompletableFuture<ExecutionResult> {
        val instrumentation = executionContext.instrumentation
        val newExecutionContext = executionContext.transform { builder: ExecutionContextBuilder ->
            builder
                .root(eventPayload)
                .resetErrors()
        }
        val newParameters = firstFieldOfSubscriptionSelection(parameters)
        val subscribedFieldStepInfo = createSubscribedFieldStepInfo(executionContext, newParameters)
        val i13nFieldParameters = InstrumentationFieldParameters(
            executionContext
        ) { subscribedFieldStepInfo }
        val subscribedFieldCtx = instrumentation.beginSubscribedFieldEvent(i13nFieldParameters)
        val fetchedValue = unboxPossibleDataFetcherResult(newExecutionContext, parameters, eventPayload)
        val fieldValueInfo = completeField(newExecutionContext, newParameters, fetchedValue)
        var overallResult = fieldValueInfo
            .fieldValue
            .thenApply { executionResult: ExecutionResult ->
                wrapWithRootFieldName(
                    newParameters,
                    executionResult
                )
            }

        // dispatch instrumentation so they can know about each subscription event
        subscribedFieldCtx.onDispatched(overallResult)
        overallResult.whenComplete { result: ExecutionResult, t: Throwable? ->
            subscribedFieldCtx.onCompleted(
                result,
                t
            )
        }

        // allow them to instrument each ER should they want to
        val i13nExecutionParameters = InstrumentationExecutionParameters(
            executionContext.executionInput, executionContext.graphQLSchema, executionContext.instrumentationState
        )
        overallResult = overallResult.thenCompose { executionResult: ExecutionResult? ->
            instrumentation.instrumentExecutionResult(
                executionResult,
                i13nExecutionParameters
            )
        }
        return overallResult
    }

    private fun wrapWithRootFieldName(
        parameters: ExecutionStrategyParameters,
        executionResult: ExecutionResult
    ): ExecutionResult {
        val rootFieldName = getRootFieldName(parameters)
        return ExecutionResultImpl(
            Collections.singletonMap(rootFieldName, executionResult.getData<Any>()),
            executionResult.errors
        )
    }

    private fun getRootFieldName(parameters: ExecutionStrategyParameters): String {
        val rootField = parameters.field.singleField
        return rootField.resultKey
    }

    private fun firstFieldOfSubscriptionSelection(parameters: ExecutionStrategyParameters): ExecutionStrategyParameters {
        val fields = parameters.fields
        val firstField = fields.getSubField(fields.keys[0])
        val fieldPath = parameters.path.segment(mkNameForPath(firstField.singleField))
        return parameters.transform { builder: ExecutionStrategyParameters.Builder ->
            builder.field(
                firstField
            ).path(fieldPath)
        }
    }

    private fun createSubscribedFieldStepInfo(
        executionContext: ExecutionContext,
        parameters: ExecutionStrategyParameters
    ): ExecutionStepInfo {
        val field = parameters.field.singleField
        val parentType = parameters.executionStepInfo.unwrappedNonNullType as GraphQLObjectType
        val fieldDef = getFieldDef(executionContext.graphQLSchema, parentType, field)
        return createExecutionStepInfo(executionContext, parameters, fieldDef, parentType)
    }
}
