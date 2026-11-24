package com.jarvis.assistant.ai

import com.jarvis.assistant.actions.PlannedAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
private data class BackendChatRequest(
    val message: String,
    val model: String,
    val history: List<BackendHistoryItem>,
    val memories: Map<String, String>
)

@Serializable
private data class BackendHistoryItem(
    val role: String,
    val text: String
)

@Serializable
private data class BackendChatResponse(
    val reply: String,
    val plannedAction: PlannedAction? = null,
    val modelUsed: String = "gemini-4-hydrogen",
    val error: String? = null
)

class GeminiBackendAiProvider(
    private val backendBaseUrl: String
) : AiProvider {

    override val providerId: String = "gemini-backend"
    override val displayName: String = "Google Gemini (Backend Proxy / Firebase AI Logic)"

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun generateReply(request: AiBrainRequest): Result<AiBrainResponse> =
        withContext(Dispatchers.IO) {
            val cleanBase = backendBaseUrl.trim().trimEnd('/')
            if (cleanBase.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("رابط الخادم غير مُعد. أضف JARVIS_BACKEND_URL في local.properties أو إعدادات التطبيق.")
                )
            }

            val start = System.currentTimeMillis()
            try {
                val payload = BackendChatRequest(
                    message = request.userMessage,
                    model = request.modelName,
                    history = request.history.takeLast(10).map {
                        BackendHistoryItem(role = it.role.name.lowercase(), text = it.text)
                    },
                    memories = request.userMemories
                )
                val body = json.encodeToString(payload)
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

                val httpReq = Request.Builder()
                    .url("$cleanBase/api/jarvis/chat")
                    .post(body)
                    .build()

                client.newCall(httpReq).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("خطأ من خادم Gemini (${resp.code}): ${raw.take(180)}")
                        )
                    }
                    val parsed = json.decodeFromString<BackendChatResponse>(raw)
                    if (!parsed.error.isNullOrBlank()) {
                        return@withContext Result.failure(IllegalStateException(parsed.error))
                    }
                    Result.success(
                        AiBrainResponse(
                            replyEgyptianArabic = parsed.reply,
                            plannedAction = parsed.plannedAction,
                            providerName = displayName,
                            modelUsed = parsed.modelUsed,
                            latencyMs = System.currentTimeMillis() - start
                        )
                    )
                }
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val cleanBase = backendBaseUrl.trim().trimEnd('/')
        if (cleanBase.isBlank()) {
            return@withContext Result.failure(IllegalStateException("JARVIS_BACKEND_URL غير مضبوط."))
        }
        try {
            val req = Request.Builder().url("$cleanBase/api/jarvis/health").get().build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    Result.success("الاتصال بخادم Gemini شغال تمام (${resp.code})")
                } else {
                    Result.failure(IllegalStateException("فشل فحص الاتصال (${resp.code})"))
                }
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}
