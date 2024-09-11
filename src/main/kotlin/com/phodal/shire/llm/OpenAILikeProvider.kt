package com.phodal.shire.llm

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.phodal.shire.custom.CustomSSEHandler
import com.phodal.shire.custom.appendCustomHeaders
import com.phodal.shire.custom.updateCustomFormat
import com.phodal.shire.settings.ShireSettingsState
import com.phodal.shirecore.llm.ChatMessage
import com.phodal.shirecore.llm.ChatRole
import com.phodal.shirecore.llm.CustomRequest
import com.phodal.shirecore.llm.LlmProvider
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Duration

data class CustomFields(
    val model: String,
    val temperature: Double,
    val maxTokens: Int?,
    val stream: Boolean
)

class OpenAILikeProvider : CustomSSEHandler(), LlmProvider {
    private val timeout = Duration.ofSeconds(defaultTimeout)

    private var modelName: String = ShireSettingsState.getInstance().modelName
    private var temperature: Float  = ShireSettingsState.getInstance().temperature
    private var maxTokens: Int? = null
    private var key: String = ShireSettingsState.getInstance().apiToken
    private var url: String = ShireSettingsState.getInstance().apiHost.ifEmpty {
        "https://api.openai.com/v1/chat/completions"
    }

    private val messages: MutableList<ChatMessage> = ArrayList()
    private var client = OkHttpClient()

    override val requestFormat: String get() = if (maxTokens != null) {
        """{ "customFields": {"model": "$modelName", "temperature": $temperature, "max_tokens": $maxTokens, "stream": true} }"""
    } else {
        """{ "customFields": {"model": "$modelName", "temperature": $temperature, "stream": true} }"""
    }
    override val responseFormat: String get() = "\$.choices[0].delta.content"
    override var project: Project? = null
    override fun clearMessage() = messages.clear()

    override fun isApplicable(project: Project): Boolean {
        this.project = project
        // dynamic check for the API key and model name
        return ShireSettingsState.getInstance().apiToken.isNotEmpty()
                && ShireSettingsState.getInstance().modelName.isNotEmpty()
    }

    override fun stream(promptText: String, systemPrompt: String, keepHistory: Boolean): Flow<String> {
        configRunLlm().let {
            it?.let {
                modelName = it.model
                temperature = it.temperature.toFloat()
                key = it.apiKey
                url = it.apiBase
                maxTokens = it.maxTokens
            }
        }

        if (!keepHistory) {
            clearMessage()
        }

        messages += ChatMessage(ChatRole.user, promptText)

        val customRequest = CustomRequest(messages)
        val requestContent = customRequest.updateCustomFormat(requestFormat)

        val body = requestContent.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val builder = Request.Builder()
        if (key.isNotEmpty()) {
            builder.addHeader("Authorization", "Bearer $key")
            builder.addHeader("Content-Type", "application/json")
        }
        builder.appendCustomHeaders(requestFormat)

        logger<OpenAILikeProvider>().info("Requesting form: $requestContent $body")

        client = client.newBuilder().readTimeout(timeout).build()
        val call = client.newCall(builder.url(url).post(body).build())

        if (!keepHistory) {
            clearMessage()
        }

        return streamSSE(call, messages)
    }
}
