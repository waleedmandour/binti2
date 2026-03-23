package com.binti.dilink.dilink

import android.content.Context
import com.binti.dilink.nlp.Intent

data class CommandResult(
    val success: Boolean,
    val message: String
)

class DiLinkCommandExecutor(private val context: Context) {
    suspend fun execute(intent: Intent): CommandResult = CommandResult(true, "OK")
}
