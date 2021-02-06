package marais.graphql.ktor.ws

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestMessages {

    @Test
    fun testSerialization() {
        val msg = Message.ConnectionInit(emptyMap())
        assertEquals(
            "{\"payload\":{}}",
            Json.encodeToString(Message.ConnectionInit.serializer(), msg),
            "Non polymorphic serialization"
        )
        assertEquals(
            "{\"type\":\"connection_init\",\"payload\":{}}",
            Json.encodeToString(Message.serializer(), msg),
            "Polymorphic serialization"
        )
        assertEquals(
            msg,
            Json.decodeFromString(Message.ConnectionInit.serializer(), "{\"payload\":{}}"),
            "Non polymorphic deserialization"
        )
        assertEquals(
            msg,
            Json.decodeFromString(Message.serializer(), "{\"type\":\"connection_init\",\"payload\":{}}"),
            "Polymorphic deserialization"
        )
        assertTrue(
            Json.decodeFromString(
                Message.serializer(),
                "{\"type\":\"connection_init\",\"payload\":{}}"
            ) is Message.ConnectionInit,
            "Correct instancing"
        )
    }
}