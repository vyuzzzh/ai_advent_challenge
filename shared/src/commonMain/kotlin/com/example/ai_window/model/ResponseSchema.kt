package com.example.ai_window.model

import kotlinx.serialization.json.*

/**
 * JSON Schema definition for structured AI responses
 * Reference: https://json-schema.org/
 */
object ResponseSchema {
    /**
     * Generates JSON Schema for AI response format
     */
    fun getSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("response") {
                put("type", "object")
                putJsonObject("properties") {
                    // Title field
                    putJsonObject("title") {
                        put("type", "string")
                        put("description", "Short title or summary of the response")
                    }
                    // Content field
                    putJsonObject("content") {
                        put("type", "string")
                        put("description", "The main response text")
                    }
                    // Metadata field
                    putJsonObject("metadata") {
                        put("type", "object")
                        putJsonObject("properties") {
                            // Confidence field
                            putJsonObject("confidence") {
                                put("type", "number")
                                put("minimum", 0.0)
                                put("maximum", 1.0)
                                put("description", "Confidence level from 0.0 (no confidence) to 1.0 (very confident)")
                            }
                            // Category field
                            putJsonObject("category") {
                                put("type", "string")
                                put("enum", JsonArray(listOf(
                                    JsonPrimitive("factual"),
                                    JsonPrimitive("opinion"),
                                    JsonPrimitive("suggestion"),
                                    JsonPrimitive("error"),
                                    JsonPrimitive("general")
                                )))
                                put("description", "Response category type")
                            }
                        }
                        put("required", JsonArray(listOf(
                            JsonPrimitive("confidence"),
                            JsonPrimitive("category")
                        )))
                    }
                }
                put("required", JsonArray(listOf(
                    JsonPrimitive("title"),
                    JsonPrimitive("content"),
                    JsonPrimitive("metadata")
                )))
            }
        }
        put("required", JsonArray(listOf(JsonPrimitive("response"))))
    }

}
