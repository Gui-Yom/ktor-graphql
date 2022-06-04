package marais.graphql.ktor

import marais.graphql.ktor.data.Message
import kotlin.test.Test
import kotlin.test.assertEquals

class TestMessages {

    @Test
    fun testSerialization() {
        val msg: Message = Message.ConnectionInit(emptyMap())
        assertEquals(
            "{\"type\":\"connection_init\",\"payload\":{}}",
            MAPPER.writeValueAsString(msg),
            "Polymorphic serialization"
        )
        assertEquals(
            msg,
            MAPPER.readValue("{\"type\":\"connection_init\",\"payload\":{}}", Message::class.java),
            "Polymorphic deserialization"
        )
    }
}