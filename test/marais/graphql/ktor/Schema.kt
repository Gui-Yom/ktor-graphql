package marais.graphql.ktor

import graphql.ErrorClassification
import graphql.GraphQLContext
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import marais.graphql.dsl.GraphQLSchema
import marais.graphql.ktor.data.GraphQLException

object Query {
    fun number(): Int = 42

    fun envConsumer(ctx: GraphQLContext): Int {
        return ctx["x-req-id"]
    }

    fun restrictedInfo() = RestrictedInfo("sensitive info")

    fun throwError(env: DataFetchingEnvironment): Boolean = throw FetchingError()
}

class FetchingError : GraphQLException("This is my fetching error", ExceptionCode.FETCHING_ERROR)

enum class ExceptionCode : ErrorClassification {
    FETCHING_ERROR
}

class RestrictedInfo(val restrictedField: String)

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

internal val testSchema = GraphQLSchema {
    query(Query)
    subscription(Subscription)
    type<RestrictedInfo> {
        "restrictedField" { ctx: GraphQLContext ->
            val secret = ctx.get<Int>("secret")
            if (secret == 42) restrictedField else null
        }
    }
}
