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
import com.jarvis.assistant.ui.DashboardUiState
import com.jarvis.assistant.ui.theme.JarvisColors
import com.jarvis.assistant.voice.TtsStatus

@Composable
fun SettingsScreen(
    uiState: DashboardUiState,
    settings: JarvisSettingsState,
    ttsStatus: TtsStatus,
    onBack: () -> Unit,
    onSaveBackendUrl: (String) -> Unit,
    onTestConnection: () -> Unit,
    onUpdateVoice: (Float, Float) -> Unit,
    onTestVoice: () -> Unit,
    onToggleOfflineStt: (Boolean) -> Unit,
    onToggleMemory: (Boolean) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onClearMemories: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    var backendUrlInput by remember(settings.backendUrl) { mutableStateOf(settings.backendUrl) }
    var speechRate by remember(settings.voiceProfile.speechRate) { mutableFloatStateOf(settings.voiceProfile.speechRate) }
    var speechPitch by remember(settings.voiceProfile.pitch) { mutableFloatStateOf(settings.voiceProfile.pitch) }

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

        SettingsSection("AI BRAIN · Gemini") {
            Text("المزود الأساسي: Google Gemini (عبر خادم Backend آمن بدون مفاتيح داخل التطبيق)", color = JarvisColors.TextSecondary, fontSize = 12.sp)
            Text("النموذج الحالي: ${settings.selectedModel}", color = JarvisColors.TextPrimary, fontSize = 13.sp)
            OutlinedTextField(
                value = backendUrlInput,
                onValueChange = { backendUrlInput = it },
                label = { Text("JARVIS Backend URL") },
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
            Text("المحرك الفعلي: ${ttsStatus.activeEngineName} · الصوت المختار: ${ttsStatus.selectedVoiceName ?: "افتراضي"}", color = JarvisColors.TextSecondary, fontSize = 12.sp)
            ttsStatus.errorNote?.let { Text(it, color = JarvisColors.StatusWarning, fontSize = 12.sp) }

            Text("السرعة: ${"%.2f".format(speechRate)}", color = JarvisColors.TextPrimary, fontSize = 12.sp)
            Slider(value = speechRate, onValueChange = { speechRate = it }, valueRange = 0.7f..1.4f)

            Text("طبقة الصوت (Pitch): ${"%.2f".format(speechPitch)}", color = JarvisColors.TextPrimary, fontSize = 12.sp)
            Slider(value = speechPitch, onValueChange = { speechPitch = it }, valueRange = 0.75f..1.25f)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onUpdateVoice(speechRate, speechPitch) },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.ElectricBlue)
                ) { Text("حفظ إعدادات الصوت") }
                OutlinedButton(onClick = onTestVoice) { Text("تجربة صوت JARVIS", color = JarvisColors.TextPrimary) }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("تفضيل التعرف على الكلام بدون إنترنت (STT Offline)", color = JarvisColors.TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Switch(checked = settings.preferOfflineStt, onCheckedChange = onToggleOfflineStt)
            }
        }

        SettingsSection("WAKE WORD & HONOR MagicOS") {
            Text("المرحلة الحالية (Phase 1): التفعيل اليدوي بالميكروفون مُفعّل. الكشف المحلي المستمر لكلمة \"JARVIS\" مجدول للمرحلة الثانية بعد التحقق من تراخيص محرك الصوت وقيود HONOR MagicOS.", color = JarvisColors.TextSecondary, fontSize = 12.sp)
            Text("الجهاز المكتشف: ${uiState.deviceInfo.manufacturer} ${uiState.deviceInfo.model} · Android ${uiState.deviceInfo.androidVersion} · MagicOS: ${uiState.deviceInfo.magicOsVersion ?: "غير معلن عبر SystemProperties"}", color = JarvisColors.TextPrimary, fontSize = 12.sp)
            Text("استثناء توفير البطارية: ${if (uiState.permissions.ignoringBatteryOptimizations) "مُفعّل" else "غير مُفعّل"}", color = JarvisColors.TextSecondary, fontSize = 12.sp)
            OutlinedButton(onClick = onOpenBatterySettings) {
                Text("فتح إعدادات بطارية MagicOS / Android", color = JarvisColors.TextPrimary)
            }
        }

        SettingsSection("DEVICE CONTROL & PERMISSIONS") {
            Text("الميكروفون (RECORD_AUDIO): ${if (uiState.permissions.microphoneGranted) "مُفعّل" else "غير مُفعّل"}", color = JarvisColors.TextPrimary, fontSize = 12.sp)
            Text("الإشعارات (POST_NOTIFICATIONS): ${if (uiState.permissions.notificationsGranted) "مُفعّل" else "غير مُفعّل"}", color = JarvisColors.TextPrimary, fontSize = 12.sp)
            Text("المكالمات وSMS وقراءة الإشعارات والوصول: مجدولة للمرحلة 3 و 4 ولا تُطلب صلاحياتها قبل التنفيذ الفعلي.", color = JarvisColors.TextSecondary, fontSize = 12.sp)
            OutlinedButton(onClick = onOpenAppSettings) {
                Text("فتح إعدادات صلاحيات التطبيق", color = JarvisColors.TextPrimary)
            }
        }

        SettingsSection("MEMORY & PRIVACY") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("تفعيل الذاكرة المحلية المعتمدة", color = JarvisColors.TextPrimary, fontSize = 13.sp)
                Switch(checked = settings.memoryEnabled, onCheckedChange = onToggleMemory)
            }
            if (settings.memories.isEmpty()) {
                Text("لا توجد عناصر محفوظة في الذاكرة.", color = JarvisColors.TextSecondary, fontSize = 12.sp)
            } else {
                settings.memories.forEach { (k, v) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("$k: $v", color = JarvisColors.TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onDeleteMemory(k) }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = JarvisColors.StatusError)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClearMemories) { Text("مسح الذاكرة", color = JarvisColors.StatusError) }
                OutlinedButton(onClick = onClearHistory) { Text("مسح سجل المحادثة", color = JarvisColors.TextPrimary) }
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
