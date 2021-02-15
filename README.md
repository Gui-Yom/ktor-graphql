# ktor-graphql

## Features

A ready to configure graphql engine based on graphql-kotlin

### GraphQL ktor feature

Exposes the graphql engine as a feature to your application. Automatically configures a graphql http endpoint based on
the specified configuration. Can also add a graphql over websocket endpoint.

### Subobjectives

Replace jackson with kotlinx-serialization

## Examples

Example usage :

```kotlin
install(GraphQLEngine) {
    graphqlConfig {
        schema(MySchema)
    }
}

install(ContentNegotiation) {
    json()
}

routing {
    graphql("/graphql") {
        // Do something before handling graphql like authentication
    }
}
```

Example usage (with Flow support in subscriptions, with `graphql-kotlin`) :

```kotlin
install(WebSockets)

install(GraphQLEngine) {
    allowGraphQLOverWS = true
    graphqlConfig {
        schema(
            toSchema(
                config = SchemaGeneratorConfig(
                    supportedPackages = listOf("mypackage"),
                    hooks = FlowSubscriptionSchemaGeneratorHooks()
                ),
                queries = listOf(TopLevelObject(Query)),
                subscriptions = listOf(TopLevelObject(Subscription))
            )
        )
        subscriptionExecutionStrategy(FlowSubscriptionExecutionStrategy())
    }
}

install(ContentNegotiation) {
    json()
}

routing {
    graphql("/graphql") {
        // Do something before handling graphql like authentication
    }
}
```

## TODO

- [x] Basic graphql engine configuration and feature
- [x] Http endpoint POST (only json body)
- [x] Http endpoint GET
- [ ] Subscription support via SSE
- [x] Graphql over websocket
- [x] Subscription support via websocket (basic)
- [ ] Complete [graphql-ws](https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md) spec impl
- [ ] Allow customizing GraphQLContext and DataloaderRegistry
