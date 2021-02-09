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

package marais.graphql.ktor.types

import kotlinx.serialization.Serializable

/**
 * Location describing which part of GraphQL document caused an exception.
 *
 * @see [GraphQL Specification](http://spec.graphql.org/June2018/#sec-Errors) for additional details
 */
@Serializable
data class SourceLocation(
    val line: Int,
    val column: Int
)
