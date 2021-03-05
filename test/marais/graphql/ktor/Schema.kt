package marais.graphql.ktor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import marais.graphql.generator.SchemaGenerator

object Query {
    fun number(): Int = 42
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

internal val testSchema = SchemaGenerator {
    query(Query) { derive() }
    subscription(Subscription) { derive() }
}.build()
