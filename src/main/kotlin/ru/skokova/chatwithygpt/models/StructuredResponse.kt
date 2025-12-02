package ru.skokova.chatwithygpt.models

import kotlinx.serialization.Serializable

@Serializable
data class StructuredResponse(val subject: String, val idea: String, val goal: String)
