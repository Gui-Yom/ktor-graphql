/*
 * Copyright 2020 Expedia, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package marais.graphql.ktor.exception

import graphql.ErrorClassification
import graphql.ErrorType
import graphql.GraphQLError
import graphql.language.SourceLocation

/**
 * Generic implementation of [GraphQLError].
 */
open class KotlinGraphQLError(
    private val exception: Throwable,
    private val locations: List<SourceLocation>? = null,
    private val path: List<Any>? = null,
    private val errorType: ErrorClassification = ErrorType.DataFetchingException
) : GraphQLError {
    override fun getErrorType(): ErrorClassification = errorType

    override fun getExtensions(): Map<String, Any> =
        if (exception is GraphQLError && exception.extensions != null) {
            exception.extensions
        } else {
            emptyMap()
        }

    override fun getLocations(): List<SourceLocation>? = locations

    override fun getMessage(): String =
        "Exception while fetching data (${path?.joinToString("/").orEmpty()}) : ${exception.message}"

    override fun getPath(): List<Any>? = path
}
