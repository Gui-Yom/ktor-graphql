package marais.graphql.ktor

import graphql.ExecutionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import marais.graphql.dsl.test.withSchema
import marais.graphql.ktor.execution.FlowSubscriptionExecutionStrategy
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertIs

class TestExecution {

    @Test
    fun testFlowSubscriptionExecutionStrategy() = withSchema({
        doNotConvertFlowToPublisher()

        query {
            "empty" { -> 0 }
        }

        subscription {
            "test" { -> flowOf("hello", "world") }
        }
    }, {
        subscriptionExecutionStrategy(FlowSubscriptionExecutionStrategy())
    }) {
        // Flow should pass through and create a correct subscription
        withQuery("""subscription { test }""") {
            assertIs<Flow<*>>(getData<Any?>())
            // Eww
            runBlocking {
                assertContentEquals(
                    listOf("hello", "world"),
                    getData<Flow<ExecutionResult>>().toList().map { it.getData<Map<String, String>>()["test"] })
            }
        }
    }
}
