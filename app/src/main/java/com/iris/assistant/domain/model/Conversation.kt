package com.iris.assistant.domain.model

data class Conversation(
    val id          : Long   = 0,
    val title       : String = "Yeni Sohbet",
    val createdAtMs : Long   = System.currentTimeMillis(),
    val updatedAtMs : Long   = System.currentTimeMillis(),
)