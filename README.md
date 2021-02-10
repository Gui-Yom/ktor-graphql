# ktor-graphql

## Features

A ready to configure graphql engine based on graphql-kotlin

### GraphQL ktor feature

Exposes the graphql engine as a feature to your application. Automatically configures a graphql http endpoint based on
the specified configuration. Can also add a graphql over websocket endpoint.

### Subobjectives

Replace jackson with kotlinx-serialization

## Examples

Example ktor integration :

```kotlin
install(GraphQLEngine) {
    graphqlConfig {
        schema(MySchema)
    }
}

install(ContentNegotiation) {
    json(json)
}

routing {
    graphql("/graphql") {
        // Do something before handling graphql like authentication
    }
}
```

## TODO

- [x] Basic graphql engine configuration and feature
- [x] Http endpoint POST
- [ ] Http endpoint GET
- [ ] Subscription support via SSE
- [ ] Graphql over websocket
- [ ] Subscription support via websocket
