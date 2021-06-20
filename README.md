# ktor-graphql

## Features

A graphql feature for Ktor.

### GraphQL ktor feature

Exposes the graphql engine as a feature to your application. Automatically configures a graphql http endpoint based on
the specified configuration. Can also add a graphql over websocket endpoint.

## Examples

Example usage with http methods (no subscriptions) and websockets :

```kotlin
// For graphql-over-ws support
install(WebSockets)
// You might want to install the Deflate Websocket extension because some client libraries use it by default

install(GraphQLEngine) {
    schema = testSchema
}

install(ContentNegotiation) {
    jackson {
        registerModule(KotlinModule())
    }
}

routing {
    graphql("/graphql") {
        // Do something before handling graphql like authentication
        // The returned object will be the graphql context
        // return null to prevent processing the request
    }
    graphqlWS("/graphql") {
        // Do something before handling graphql like authentication
        // You get access to the initial payload Map
        // The returned object will be the graphql context
        // return null to prevent processing the request
    }
}
```

## TODO

- [x] Basic graphql engine configuration and feature
- [x] Http endpoint POST (only json body)
- [x] Http endpoint GET
- [x] Graphql over websocket
- [x] Subscription support via websocket (basic)
- [ ] Use the serialization provided by ktor ?
- [ ] Allow customizing GraphQLContext and DataloaderRegistry
- [ ] Complete [graphql-ws](https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md) spec impl
- [ ] Subscription support via SSE
