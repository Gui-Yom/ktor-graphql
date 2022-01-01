package marais.graphql.ktor.data

import graphql.ExecutionInput
import graphql.GraphQLContext
import org.dataloader.DataLoaderRegistry

/**
 * GraphQL request that follows the common GraphQL HTTP request format and works with serialization and deserialization.
 *
 * @see [GraphQL Over HTTP](https://graphql.org/learn/serving-over-http/#post-request) for additional details
 */
data class GraphQLRequest(
    val query: String,
    val operationName: String? = null,
    val variables: Map<String, Any?>? = null
) {
    /**
     * Convert the common [GraphQLRequest] to the execution input used by graphql-java
     */
    fun toExecutionInput(
        graphQLContext: Map<*, Any>,
        dataLoaderRegistry: DataLoaderRegistry? = null
    ): ExecutionInput {
        val builder = ExecutionInput.newExecutionInput()
            .query(this.query)
            .operationName(this.operationName)
            .graphQLContext(graphQLContext)
            .variables(this.variables ?: emptyMap())
            .dataLoaderRegistry(dataLoaderRegistry ?: DataLoaderRegistry())
        return builder.build()
    }
}
