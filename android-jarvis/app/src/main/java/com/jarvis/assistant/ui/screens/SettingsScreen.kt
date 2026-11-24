package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.memory.JarvisSettingsState
import com.jarvis.assistant.memory.MemoryCategory
import com.jarvis.assistant.ui.DashboardUiState
import com.jarvis.assistant.ui.theme.JarvisColors
import com.jarvis.assistant.voice.SttCapabilities
import com.jarvis.assistant.voice.TtsStatus

@Composable
fun SettingsScreen(
    uiState: DashboardUiState,
    settings: JarvisSettingsState,
    ttsStatus: TtsStatus,
    sttCapabilities: SttCapabilities,
    onBack: () -> Unit,
    onSaveBackendUrl: (String) -> Unit,
    onTestConnection: () -> Unit,
    onUpdateVoice: (rate: Float, pitch: Float, languageTag: String, voiceName: String?) -> Unit,
    onTestVoice: () -> Unit,
    onToggleOfflineStt: (Boolean) -> Unit,
    onUpdateSttLanguage: (String) -> Unit,
    onToggleMemory: (Boolean) -> Unit,
    onToggleShareMemoriesWithAi: (Boolean) -> Unit,
    onSaveManualMemory: (key: String, value: String, category: MemoryCategory) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onClearAllMemoriesConfirmed: () -> Unit,
    onRecoverCorruptedMemory: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    var backendUrlInput by remember(settings.backendUrl) { mutableStateOf(settings.backendUrl) }
    var speechRate by remember(settings.voiceProfile.speechRate) { mutableFloatStateOf(settings.voiceProfile.speechRate) }
    var speechPitch by remember(settings.voiceProfile.pitch) { mutableFloatStateOf(settings.voiceProfile.pitch) }
    var selectedVoiceName by remember(settings.voiceProfile.selectedVoiceName) {
        mutableStateOf(settings.voiceProfile.selectedVoiceName)
    }
    var voiceLanguageTag by remember(settings.voiceProfile.languageTag) {
        mutableStateOf(settings.voiceProfile.languageTag)
    }

    var memorySearch by remember { mutableStateOf("") }
    var newMemKey by remember { mutableStateOf("") }
    var newMemValue by remember { mutableStateOf("") }
    var confirmClearMemoriesDialog by remember { mutableStateOf(false) }

    if (confirmClearMemoriesDialog) {
        AlertDialog(
            onDismissRequest = { confirmClearMemoriesDialog = false },
            containerColor = JarvisColors.PanelNavy,
            title = { Text("تأكيد مسح كل الذاكرة", color = JarvisColors.TextPrimary) },
            text = { Text("سيتم حذف جميع الملاحظات والتفضيلات المحلية بشكل نهائي. هل أنت متأكد؟", color = JarvisColors.TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClearMemoriesDialog = false
                        onClearAllMemoriesConfirmed()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.StatusError)
                ) {
                    Text("نعم، امسح الكل")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmClearMemoriesDialog = false }) {
                    Text("إلغاء", color = JarvisColors.TextPrimary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisColors.VoidBlack)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = JarvisColors.CyanCore)
            }
            Text("إعدادات J.A.R.V.I.S.", color = JarvisColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        SettingsSection("AI BRAIN · Gemini Backend") {
            Text(
                "الاتصال يتم عبر خادم موثوق (HTTPS) لحماية مفاتيح Gemini وعدم تضمين أي أسرار داخل التطبيق.",
                color = JarvisColors.TextSecondary,
                fontSize = 12.sp
            )
            Text("النموذج الحالي: ${settings.selectedModel}", color = JarvisColors.TextPrimary, fontSize = 13.sp)
            OutlinedTextField(
                value = backendUrlInput,
                onValueChange = { backendUrlInput = it },
                label = { Text("JARVIS Backend URL (HTTPS)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSaveBackendUrl(backendUrlInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.CyanCore)
                ) {
                    Text("حفظ الرابط", color = JarvisColors.VoidBlack)
                }
                OutlinedButton(onClick = onTestConnection) {
                    Text("اختبار الاتصال", color = JarvisColors.TextPrimary)
                }
            }
            uiState.connectionTestResult?.let {
                Text(it, color = JarvisColors.CyanCore, fontSize = 12.sp)
            }
        }

        SettingsSection("VOICE & LANGUAGE · ملف الصوت JARVIS") {
            Text("ملف الصوت النشط: ${settings.voiceProfile.profileName}", color = JarvisColors.CyanCore, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("محرك النطق: ${ttsStatus.activeEngineName}", color = JarvisColors.TextSecondary, fontSize = 12.sp)
            Text("حالة التعرف الصوتي (STT): ${sttCapabilities.activeModeLabel}", color = JarvisColors.TextSecondary, fontSize = 12.sp)
            ttsStatus.errorNote?.let { Text(it, color = JarvisColors.StatusWarning, fontSize = 12.sp) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    voiceLanguageTag = "ar-EG"
                    onUpdateSttLanguage("ar-EG")
                }) {
                    Text("مصري (ar-EG)", color = if (voiceLanguageTag == "ar-EG") JarvisColors.CyanCore else JarvisColors.TextPrimary)
                }
                OutlinedButton(onClick = {
                    voiceLanguageTag = "en-US"
                    onUpdateSttLanguage("en-US")
                }) {
                    Text("English (en-US)", color = if (voiceLanguageTag == "en-US") JarvisColors.CyanCore else JarvisColors.TextPrimary)
                }
            }

            if (ttsStatus.availableVoices.isNotEmpty()) {
                Text("الأصوات المتاحة على الجهاز (${ttsStatus.availableVoices.size}):", color = JarvisColors.TextSecondary, fontSize = 12.sp)
                ttsStatus.availableVoices.take(6).forEach { voice ->
                    val isChosen = selectedVoiceName == voice.name
                    OutlinedButton(
                        onClick = { selectedVoiceName = voice.name },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${if (isChosen) "✓ " else ""}${voice.name} (${voice.languageTag}${if (voice.requiresNetwork) " · Online" else " · Offline"})",
                            color = if (isChosen) JarvisColors.CyanCore else JarvisColors.TextPrimary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Text("السرعة: ${"%.2f".format(speechRate)}", color = JarvisColors.TextPrimary, fontSize = 12.sp)
            Slider(value = speechRate, onValueChange = { speechRate = it }, valueRange = 0.7f..1.4f)

            Text("طبقة الصوت (Pitch): ${"%.2f".format(speechPitch)}", color = JarvisColors.TextPrimary, fontSize = 12.sp)
            Slider(value = speechPitch, onValueChange = { speechPitch = it }, valueRange = 0.75f..1.25f)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onUpdateVoice(speechRate, speechPitch, voiceLanguageTag, selectedVoiceName) },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.ElectricBlue)
                ) { Text("حفظ إعدادات الصوت") }
                OutlinedButton(onClick = onTestVoice) { Text("تجربة صوت JARVIS", color = JarvisColors.TextPrimary) }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("تفضيل التعرف المحلي بدون إنترنت (On-Device / Offline)", color = JarvisColors.TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Switch(checked = settings.preferOfflineStt, onCheckedChange = onToggleOfflineStt)
            }
        }

        SettingsSection("WAKE WORD · تجهيز المرحلة الثانية") {
            Text(
                "كشف كلمة \"JARVIS\" الدائم غير مفعّل في المرحلة 1.1 (واجهة WakeWordEngine مجهزة فقط بدون استماع صامت). يتطلب خدمة FOREGROUND_SERVICE_MICROPHONE واستثناء بطارية HONOR MagicOS.",
                color = JarvisColors.TextSecondary,
                fontSize = 12.sp
            )
            Text(
                "الجهاز المكتشف: ${uiState.deviceInfo.manufacturer} ${uiState.deviceInfo.model} · Android ${uiState.deviceInfo.androidVersion} · MagicOS: ${uiState.deviceInfo.magicOsVersion ?: "غير معلن"}",
                color = JarvisColors.TextPrimary,
                fontSize = 12.sp
            )
            Text(
                "استثناء توفير البطارية: ${if (uiState.permissions.ignoringBatteryOptimizations) "مُفعّل" else "غير مُفعّل"}",
                color = JarvisColors.TextSecondary,
                fontSize = 12.sp
            )
            OutlinedButton(onClick = onOpenBatterySettings) {
                Text("فتح إعدادات بطارية MagicOS / Android", color = JarvisColors.TextPrimary)
            }
        }

        SettingsSection("PERMISSIONS & SECURITY") {
            Text("الميكروفون (RECORD_AUDIO): ${if (uiState.permissions.microphoneGranted) "مُفعّل" else "غير مُفعّل"}", color = JarvisColors.TextPrimary, fontSize = 12.sp)
            Text("الإشعارات (POST_NOTIFICATIONS): ${if (uiState.permissions.notificationsGranted) "مُفعّل" else "غير مُفعّل"}", color = JarvisColors.TextPrimary, fontSize = 12.sp)
            OutlinedButton(onClick = onOpenAppSettings) {
                Text("فتح إعدادات صلاحيات التطبيق", color = JarvisColors.TextPrimary)
            }
        }

        SettingsSection("MEMORY & PRIVACY") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("تفعيل الذاكرة المحلية المعتمدة", color = JarvisColors.TextPrimary, fontSize = 13.sp)
                Switch(checked = settings.memoryEnabled, onCheckedChange = onToggleMemory)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("مشاركة سياق الذاكرة ذي الصلة مع Gemini فقط", color = JarvisColors.TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Switch(checked = settings.shareMemoriesWithAi, onCheckedChange = onToggleShareMemoriesWithAi)
            }

            if (settings.memoryIntegrity.hasCorruptedBackup) {
                Text(
                    settings.memoryIntegrity.corruptionMessage ?: "تم رصد تلف في بيانات الذاكرة.",
                    color = JarvisColors.StatusWarning,
                    fontSize = 12.sp
                )
                OutlinedButton(onClick = onRecoverCorruptedMemory) {
                    Text("عزل النسخة التالفة واستعادة الذاكرة", color = JarvisColors.CyanCore)
                }
            }

            OutlinedTextField(
                value = newMemKey,
                onValueChange = { newMemKey = it },
                label = { Text("عنوان المعلومة") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = newMemValue,
                onValueChange = { newMemValue = it },
                label = { Text("القيمة") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = {
                    if (newMemKey.isNotBlank() && newMemValue.isNotBlank()) {
                        onSaveManualMemory(newMemKey, newMemValue, MemoryCategory.PERSONAL_NOTE)
                        newMemKey = ""
                        newMemValue = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.CyanCore)
            ) {
                Text("حفظ في الذاكرة المحلية", color = JarvisColors.VoidBlack)
            }

            OutlinedTextField(
                value = memorySearch,
                onValueChange = { memorySearch = it },
                label = { Text("بحث في الذاكرة...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            val filteredMemories = remember(settings.memories, memorySearch) {
                val q = memorySearch.trim().lowercase()
                if (q.isBlank()) settings.memories
                else settings.memories.filter {
                    it.displayKey.lowercase().contains(q) || it.value.lowercase().contains(q)
                }
            }

            if (filteredMemories.isEmpty()) {
                Text("لا توجد عناصر مطابقة في الذاكرة.", color = JarvisColors.TextSecondary, fontSize = 12.sp)
            } else {
                filteredMemories.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${entry.displayKey}: ${entry.value}", color = JarvisColors.TextPrimary, fontSize = 12.sp)
                            Text(entry.category.arabicLabel, color = JarvisColors.TextSecondary, fontSize = 10.sp)
                        }
                        IconButton(onClick = { onDeleteMemory(entry.normalizedKey) }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = JarvisColors.StatusError)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { confirmClearMemoriesDialog = true }) {
                    Text("مسح كل الذاكرة", color = JarvisColors.StatusError)
                }
                OutlinedButton(onClick = onClearHistory) {
                    Text("مسح سجل المحادثة", color = JarvisColors.TextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(JarvisColors.DeepNavy, RoundedCornerShape(14.dp))
            .border(1.dp, JarvisColors.HairlineBlue, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = JarvisColors.CyanCore, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        content()
    }
}
