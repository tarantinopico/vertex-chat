package com.example

import com.example.ai.StreamEvent
import com.example.ai.VertexSseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class ExampleUnitTest {

    @Test
    fun `sse parser extracts thought delta, text delta, functionCall and MAX_TOKENS finishReason`() {
        val rawSseStream = """
            data: {"candidates":[{"content":{"role":"model","parts":[{"thought":true,"text":"Analyzuji požadavek na vytvoření souboru..."}]}}]}

            data: {"candidates":[{"content":{"role":"model","parts":[{"text":"Zde je návrh řešení v **Kotlinu**:\n"}]}}]}

            data: {"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"writeFile","args":{"path":"/src/App.kt","content":"fun main() = println(42)","append":false}}}]}}]}

            data: {"candidates":[{"content":{"role":"model","parts":[{"text":"Hotovo."}]},"finishReason":"MAX_TOKENS"}]}

            data: [DONE]
        """.trimIndent()

        val events = mutableListOf<StreamEvent>()
        VertexSseParser.parseSseStream(BufferedReader(StringReader(rawSseStream))) { event ->
            events.add(event)
        }

        val thoughts = events.filterIsInstance<StreamEvent.ThoughtDelta>()
        val texts = events.filterIsInstance<StreamEvent.TextDelta>()
        val fnCalls = events.filterIsInstance<StreamEvent.FunctionCallEvent>()
        val finishes = events.filterIsInstance<StreamEvent.Finish>()

        assertEquals(1, thoughts.size)
        assertEquals("Analyzuji požadavek na vytvoření souboru...", thoughts.first().text)

        assertEquals(2, texts.size)
        assertEquals("Zde je návrh řešení v **Kotlinu**:\nHotovo.", texts.joinToString("") { it.text })

        assertEquals(1, fnCalls.size)
        assertEquals("writeFile", fnCalls.first().name)
        assertTrue(fnCalls.first().args.toString().contains("/src/App.kt"))

        assertEquals(1, finishes.size)
        assertEquals("MAX_TOKENS", finishes.first().finishReason)
    }
}
