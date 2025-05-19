package com.example.myapplication

import com.google.gson.annotations.SerializedName

data class DockerRequest(
    val model: String,
    val messages: List<MCPMessage>,
    val tools: List<MCPTool>? = null,
    val max_tokens: Int? = null,
    val temperature: Float? = null

)

data class MCPMessage(
    val role: String,
    val content: String
)

data class MCPTool(
    val type: String,
    val function: MCPFunction
)

data class MCPFunction(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>
)

data class MCPResponse(
    val id: String,
    @SerializedName("object")
    val objectType: String,
    val created: Long,
    val model: String,
    val choices: List<Choice>
)

data class Choice(
    val index: Int,
    val message: MCPResponseMessage,
    val finish_reason: String
)

data class MCPResponseMessage(
    val role: String,
    val content: String?,
    val tool_calls: List<MCPToolCall>?
)

data class MCPToolCall(
    val id: String,
    val type: String,
    val function: FunctionCall
)

data class FunctionCall(
    val name: String,
    val arguments: String
)

// Модель для события календаря
data class CalendarEvent(
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val description: String = ""
)

