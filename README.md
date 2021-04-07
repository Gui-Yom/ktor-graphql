# ktor-graphql

## Features

A graphql feature for Ktor.

### GraphQL ktor feature

Exposes the graphql engine as a feature to your application. Automatically configures a graphql http endpoint based on
the specified configuration. Can also add a graphql over websocket endpoint.

## Examples

Example usage (query/mutation operations, standard http methods only) :

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

Example usage (all operations supported, http routes + websocket) :

```kotlin
install(WebSockets)

install(GraphQLEngine) {
    allowGraphQLOverWS = true
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

## TODO

- [x] Basic graphql engine configuration and feature
- [x] Http endpoint POST (only json body)
- [x] Http endpoint GET
- [x] Graphql over websocket
- [x] Subscription support via websocket (basic)
- [ ] Allow customizing GraphQLContext and DataloaderRegistry
- [ ] Complete [graphql-ws](https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md) spec impl
- [ ] Subscription support via SSE
- [ ] Replace kotlinx.serialization with jackson because we graphql structures can only be expressed with reflection
