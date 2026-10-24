package com.example.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader

sealed class StreamEvent {
    data class ThoughtDelta(val text: String) : StreamEvent()
    data class TextDelta(val text: String) : StreamEvent()
    data class FunctionCallEvent(
        val name: String,
        val args: JsonObject,
        val rawPartJson: JsonObject
    ) : StreamEvent()
    data class Finish(val finishReason: String) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
}

/**
 * Parser pro Server-Sent Events (SSE) stream z Vertex AI / Gemini (:streamGenerateContent?alt=sse).
 * Zpracovává data po řádcích z BufferedReaderu bez načítání celého streamu do paměti.
 */
object VertexSseParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parseSseStream(
        reader: BufferedReader,
        onEvent: (StreamEvent) -> Unit
    ) {
        val dataBuffer = StringBuilder()

        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val currentLine = line ?: continue
            val trimmed = currentLine.trim()

            if (trimmed.isEmpty()) {
                if (dataBuffer.isNotEmpty()) {
                    processPayload(dataBuffer.toString().trim(), onEvent)
                    dataBuffer.clear()
                }
                continue
            }

            if (trimmed.startsWith("data:")) {
                val payloadLine = trimmed.removePrefix("data:").trim()
                if (payloadLine == "[DONE]") {
                    if (dataBuffer.isNotEmpty()) {
                        processPayload(dataBuffer.toString().trim(), onEvent)
                        dataBuffer.clear()
                    }
                    continue
                }
                // Try immediate parse if single-line JSON (standard for Vertex AI SSE)
                if (payloadLine.startsWith("{") && payloadLine.endsWith("}")) {
                    processPayload(payloadLine, onEvent)
                } else {
                    if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                    dataBuffer.append(payloadLine)
                }
            } else if (dataBuffer.isNotEmpty()) {
                dataBuffer.append('\n').append(trimmed)
            }
        }

        if (dataBuffer.isNotEmpty()) {
            processPayload(dataBuffer.toString().trim(), onEvent)
            dataBuffer.clear()
        }
    }

    fun parseSingleDataPayload(
        jsonPayload: String,
        onEvent: (StreamEvent) -> Unit
    ) {
        processPayload(jsonPayload.trim(), onEvent)
    }

    private fun processPayload(
        payload: String,
        onEvent: (StreamEvent) -> Unit
    ) {
        if (payload.isEmpty() || payload == "[DONE]") return

        try {
            val root = json.parseToJsonElement(payload).jsonObject

            // Check for explicit API error inside stream
            val errorObj = root["error"]?.jsonObject
            if (errorObj != null) {
                val msg = errorObj["message"]?.jsonPrimitive?.content ?: "Chyba streamu Vertex AI"
                onEvent(StreamEvent.Error(msg))
                return
            }

            val candidates = root["candidates"]?.jsonArray ?: return
            for (candidateElem in candidates) {
                val candidate = candidateElem.jsonObject
                val content = candidate["content"]?.jsonObject
                val parts = content?.get("parts")?.jsonArray

                if (parts != null) {
                    for (partElem in parts) {
                        val partObj = partElem.jsonObject
                        val isThought = partObj["thought"]?.jsonPrimitive?.booleanOrNull == true
                        val textVal = partObj["text"]?.jsonPrimitive?.content

                        if (textVal != null && textVal.isNotEmpty()) {
                            if (isThought) {
                                onEvent(StreamEvent.ThoughtDelta(textVal))
                            } else {
                                onEvent(StreamEvent.TextDelta(textVal))
                            }
                        }

                        val fnCall = partObj["functionCall"]?.jsonObject
                        if (fnCall != null) {
                            val fnName = fnCall["name"]?.jsonPrimitive?.content.orEmpty()
                            val fnArgs = fnCall["args"]?.jsonObject ?: JsonObject(emptyMap())
                            if (fnName.isNotBlank()) {
                                onEvent(
                                    StreamEvent.FunctionCallEvent(
                                        name = fnName,
                                        args = fnArgs,
                                        rawPartJson = partObj
                                    )
                                )
                            }
                        }
                    }
                }

                val finishReason = candidate["finishReason"]?.jsonPrimitive?.content
                if (!finishReason.isNullOrBlank()) {
                    onEvent(StreamEvent.Finish(finishReason))
                }
            }
        } catch (e: Exception) {
            onEvent(StreamEvent.Error("Chyba parsování SSE JSONu: ${e.message}"))
        }
    }
}
