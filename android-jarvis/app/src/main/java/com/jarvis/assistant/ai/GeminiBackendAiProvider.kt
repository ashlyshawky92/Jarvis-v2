package com.jarvis.assistant.ai

import com.jarvis.assistant.actions.PlannedAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
    val reply: String? = null,
    val plannedAction: PlannedAction? = null,
    val modelUsed: String = "gemini-4-hydrogen",
    val error: String? = null
)

object BackendUrlValidator {
    fun validate(rawUrl: String): Result<String> {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isBlank()) {
            return Result.failure(
                IllegalArgumentException("رابط الخادم غير مضبوط. أدخل رابط HTTPS الخاص بخادم JARVIS في الإعدادات.")
            )
        }
        val parsed = trimmed.toHttpUrlOrNull()
            ?: return Result.failure(IllegalArgumentException("صيغة رابط الخادم غير صحيحة."))

        val isLocalDebugHost = parsed.host == "localhost" ||
            parsed.host == "127.0.0.1" ||
            parsed.host == "10.0.2.2"

        if (!parsed.isHttps && !isLocalDebugHost) {
            return Result.failure(
                IllegalArgumentException("لأمان بياناتك، يجب استخدام رابط HTTPS لخادم JARVIS في الإنتاج.")
            )
        }
        return Result.success(trimmed)
    }
}

class GeminiBackendAiProvider(
    private val backendBaseUrl: String,
    private val maxHistoryTurns: Int = 10
) : AiProvider {

    override val providerId: String = "gemini-backend"
    override val displayName: String = "Google Gemini (Trusted Backend Proxy)"

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun generateReply(request: AiBrainRequest): Result<AiBrainResponse> =
        withContext(Dispatchers.IO) {
            val validatedBase = BackendUrlValidator.validate(backendBaseUrl)
                .getOrElse { return@withContext Result.failure(it) }

            val start = System.currentTimeMillis()
            // Exclude duplicate trailing user message if caller already appended current message.
            val sanitizedHistory = request.history
                .dropLastWhile { it.text.trim() == request.userMessage.trim() }
                .takeLast(maxHistoryTurns.coerceIn(0, 20))
                .map { BackendHistoryItem(role = it.role.name.lowercase(), text = it.text) }

            val payload = BackendChatRequest(
                message = request.userMessage,
                model = request.modelName,
                history = sanitizedHistory,
                memories = request.userMemories
            )
            val body = json.encodeToString(payload)
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val maxAttempts = 3
            var backoffMs = 600L

            for (attempt in 1..maxAttempts) {
                try {
                    val httpReq = Request.Builder()
                        .url("$validatedBase/api/jarvis/chat")
                        .post(body)
                        .build()

                    val (code, rawBody) = executeCancellable(client.newCall(httpReq))

                    if (code in 500..599 || code == 429) {
                        if (attempt < maxAttempts) {
                            delay(backoffMs)
                            backoffMs *= 2
                            continue
                        }
                        return@withContext Result.failure(
                            IllegalStateException("خادم Gemini مشغول أو غير متاح حالياً (كود $code). حاول بعد قليل.")
                        )
                    }

                    if (code !in 200..299) {
                        val serverErr = runCatching { json.decodeFromString<BackendChatResponse>(rawBody).error }.getOrNull()
                        return@withContext Result.failure(
                            IllegalStateException(serverErr ?: "رفض الخادم الطلب بكود ($code).")
                        )
                    }

                    if (rawBody.isBlank()) {
                        return@withContext Result.failure(IllegalStateException("وصل رد فارغ من الخادم."))
                    }

                    val parsed = runCatching { json.decodeFromString<BackendChatResponse>(rawBody) }
                        .getOrElse {
                            return@withContext Result.failure(IllegalStateException("تعذر قراءة صيغة الرد من الخادم."))
                        }

                    if (!parsed.error.isNullOrBlank()) {
                        return@withContext Result.failure(IllegalStateException(parsed.error))
                    }

                    val replyText = parsed.reply?.trim().orEmpty()
                    if (replyText.isBlank()) {
                        return@withContext Result.failure(IllegalStateException("لم يرجع المساعد بنص رد صالح."))
                    }

                    // Validate any suggested action against local allowlist before returning.
                    val allowlistedIds = request.allowlistedActions.map { it.id }.toSet()
                    val safePlanned = parsed.plannedAction?.takeIf { it.actionId in allowlistedIds }

                    return@withContext Result.success(
                        AiBrainResponse(
                            replyEgyptianArabic = replyText,
                            plannedAction = safePlanned,
                            providerName = displayName,
                            modelUsed = parsed.modelUsed,
                            latencyMs = System.currentTimeMillis() - start
                        )
                    )
                } catch (c: CancellationException) {
                    throw c
                } catch (io: IOException) {
                    if (attempt < maxAttempts) {
                        delay(backoffMs)
                        backoffMs *= 2
                        continue
                    }
                    return@withContext Result.failure(
                        IllegalStateException("تعذر الوصول للخادم عبر الشبكة: ${io.message ?: "انقطع الاتصال"}")
                    )
                } catch (t: Throwable) {
                    return@withContext Result.failure(t)
                }
            }

            Result.failure(IllegalStateException("انتهت محاولات الاتصال بالخادم."))
        }

    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val validatedBase = BackendUrlValidator.validate(backendBaseUrl)
            .getOrElse { return@withContext Result.failure(it) }
        try {
            val req = Request.Builder().url("$validatedBase/api/jarvis/health").get().build()
            val (code, raw) = executeCancellable(client.newCall(req))
            if (code in 200..299 && raw.isNotBlank()) {
                Result.success("الاتصال بخادم Gemini شغال تمام ($code)")
            } else {
                Result.failure(IllegalStateException("فشل فحص الاتصال بالخادم (كود $code)"))
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.failure(IllegalStateException("تعذر الاتصال بالخادم: ${t.message ?: "خطأ شبكة"}"))
        }
    }

    private suspend fun executeCancellable(call: Call): Pair<Int, String> =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { res ->
                        val bodyStr = res.body?.string().orEmpty()
                        if (continuation.isActive) {
                            continuation.resume(res.code to bodyStr)
                        }
                    }
                }
            })
        }
}
