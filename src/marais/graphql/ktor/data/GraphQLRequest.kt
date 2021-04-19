package marais.graphql.ktor.data

import graphql.ExecutionInput
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
        graphQLContext: Any? = null,
        dataLoaderRegistry: DataLoaderRegistry? = null
    ): ExecutionInput {
        val builder = ExecutionInput.newExecutionInput()
            .query(this.query)
            .operationName(this.operationName)
            .variables(this.variables ?: emptyMap())
            .dataLoaderRegistry(dataLoaderRegistry ?: DataLoaderRegistry())
        if (graphQLContext != null) {
            builder.context(graphQLContext)
        }
        return builder.build()
    }
}
