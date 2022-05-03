package marais.graphql.ktor

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.schema.GraphQLSchema
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import marais.graphql.ktor.data.GraphQLRequest
import marais.graphql.ktor.data.GraphQLResponse
import marais.graphql.ktor.data.toGraphQLResponse
import marais.graphql.ktor.data.toGraphQlError
import org.apache.logging.log4j.LogManager

class GraphQLPlugin(conf: Configuration) {

    private val mapper = conf.mapper
    val graphql = conf.builder!!.build()
    val log = LogManager.getLogger()

    internal suspend fun handleGet(
        ctx: PipelineContext<Unit, ApplicationCall>, builder: suspend ExecutionInput.Builder.() -> Unit
    ) {
        val query = ctx.call.request.queryParameters["query"]
        if (query == null) {
            ctx.call.respond(HttpStatusCode.BadRequest, "Missing 'query' parameter")
            return
        }
        val variables: Map<String, Any?>? = ctx.call.request.queryParameters["variables"]?.let {
            mapper.readerForMapOf(Any::class.java).readValue(it)
        }
        val input = GraphQLRequest(
            query, ctx.call.request.queryParameters["operationName"], variables
        ).toExecutionInput()
        builder(input)
        if (ctx.call.isHandled)
            return
        ctx.call.respond(handleGraphQL(input.build()))
    }

    internal suspend fun handlePost(
        ctx: PipelineContext<Unit, ApplicationCall>, builder: suspend ExecutionInput.Builder.() -> Unit
    ) {
        val json = ctx.call.receiveText()
        try {
            val input = mapper.readValue(json, GraphQLRequest::class.java).toExecutionInput()
            builder(input)
            if (ctx.call.isHandled)
                return
            ctx.call.respond(handleGraphQL(input.build()))

            // IMHO That is one terrible way of handling the case where we get an array of queries
            // But hey it works
        } catch (e: JsonMappingException) {
            val batch: Array<GraphQLRequest> = mapper.readerForArrayOf(GraphQLRequest::class.java).readValue(json)
            // Run all queries concurrently
            ctx.call.respond(mapper.writeValueAsString(coroutineScope {
                batch.map {
                    async {
                        val input = it.toExecutionInput()
                        builder(input)
                        // oh god
                        if (ctx.call.isHandled)
                            throw JumpException
                        handleGraphQL(input.build())
                    }
                }
            }.awaitAll()))
        } catch (e: JumpException) {
            // Ewwwwww
        } catch (e: Exception) {
            // TODO meaningful messages
            // TODO json error messages
            ctx.call.respond(HttpStatusCode.BadRequest, "Bad request")
        }
    }

    suspend fun handleGraphQL(
        input: ExecutionInput
    ): GraphQLResponse {
        return try {
            graphql.executeAsync(input).await().toGraphQLResponse()
        } catch (exception: Exception) {
            GraphQLResponse(errors = listOf(exception.toGraphQlError()))
        }
    }

    class Configuration {
        var mapper: ObjectMapper =
            jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        internal var builder: GraphQL.Builder? = null

        fun graphql(schema: GraphQLSchema, builder: GraphQL.Builder.() -> Unit = {}) {
            this.builder = GraphQL.newGraphQL(schema).apply(builder)
        }
    }

    companion object Plugin : BaseApplicationPlugin<Application, Configuration, GraphQLPlugin> {
        override val key = AttributeKey<GraphQLPlugin>("GraphQL")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): GraphQLPlugin {
            val conf = Configuration().apply(configure)
            if (conf.builder == null) {
                throw IllegalStateException("You must at least specify a GraphQLSchema for the GraphQLPlugin")
            }
            return GraphQLPlugin(conf)
        }
    }
}
