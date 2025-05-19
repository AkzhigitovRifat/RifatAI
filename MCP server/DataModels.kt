package com.example.myapplication

data class MessageRequest(
    val model: String,
    val messages: List<Message>,
    val tools: List<Tool>,
    val max_tokens: Int
)

data class Message(
    val role: String,
    val content: List<Content>
)

data class Content(
    val type: String,
    val text: String? = null
)

data class Tool(
    val name: String,
    val description: String,
    val input_schema: InputSchema
)

data class InputSchema(
    val type: String,
    val properties: Map<String, Property>,
    val required: List<String>
)

data class Property(
    val type: String,
    val description: String
)

data class MessageResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ResponseContent>,
    val model: String,
    val stop_reason: String?
)

data class ResponseContent(
    val type: String,
    val text: String? = null,
    val tool_use: ToolUse? = null
)

data class ToolUse(
    val id: String,
    val name: String,
    val input: Map<String, String>
)
