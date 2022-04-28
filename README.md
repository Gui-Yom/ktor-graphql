# ktor-graphql

## Features

### GraphQL ktor plugin

Exposes the graphql engine as a plugin to your application. Automatically configures a graphql http endpoint based on
the specified configuration. Can also add a graphql over websocket endpoint.

#### Websockets

The plugin supports ktor websockets but the ktor websockets library is loosely coupled. Add the following dependency to use the provided graphqlWS route :
```kotlin
implementation("io.ktor:ktor-server-websockets")
```

#### Serialization

The plugin will try to use the ktor content converters when possible but in some places it needs to make calls to
jackson directly.
Also, `kotlinx.serialization` is unsupported as a ktor content serializer since it can't de|serialize dynamic content
like `Map<String, Any>`.

#### Example

Example usage with http methods (no subscriptions) and websockets :

```kotlin
// For graphql-over-ws support
// You might want to install the Deflate Websocket extension because some client libraries use it by default
// You need to install a content converter
install(WebSockets) {
    contentConverter = JacksonWebsocketContentConverter()
}

install(GraphQLPlugin) {
    graphql(schema)
}

install(ContentNegotiation) {
    jackson {
        registerModule(KotlinModule())
    }
}

routing {
    graphql {
        // Do something before handling graphql like authentication
        // The returned object will be the graphql context
        // return null to prevent processing the request
    }
    graphqlWS {
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
- [x] Use the serialization provided by ktor (where possible)
- [x] Customization of ExecutionInput for GraphQLContext and others
- [ ] Complete [graphql-ws](https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md) spec impl
- [ ] Subscription support via SSE
