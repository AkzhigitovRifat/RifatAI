package com.example.myapplication

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface Server {
    @POST("api/v1/chat/completions")
    suspend fun generateCompletion(
        @Header("Authorization") authorization: String,
        @Body request: DockerRequest
    ): MCPResponse
}
