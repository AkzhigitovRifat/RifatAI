package com.example.myapplication

sealed class UiState {
    object Initial : UiState()
    object Loading : UiState()
    data class Success(val modelResponse: String, val functionCallDetails: String) : UiState()
    data class Error(val message: String) : UiState()
}
