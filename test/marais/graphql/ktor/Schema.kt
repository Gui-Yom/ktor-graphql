package marais.graphql.ktor

import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import marais.graphql.dsl.SchemaBuilder

data class ContextObject(val reqId: Int)

object Query {
    fun number(): Int = 42

    fun envConsumer(env: DataFetchingEnvironment): Int {
        return env.getContext<ContextObject>().reqId
    }
}

object Subscription {
    /**
     * Emits the integer 42, 3 times with a 200 ms interval
     */
    fun number(): Flow<Int> = flow {
        for (i in 0..2) {
            delay(200)
            emit(42)
        }
    }
}

internal val testSchema = SchemaBuilder {
    query(Query) { derive() }
    subscription(Subscription) { derive() }
}.build()
