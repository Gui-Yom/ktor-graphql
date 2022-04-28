package marais.graphql.ktor

object BadFrameException : Exception("Expected text frame, got binary frame")

object JumpException : Exception()
