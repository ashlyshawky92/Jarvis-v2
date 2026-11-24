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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.core.model.AssistantState
import com.jarvis.assistant.ui.DashboardUiState
import com.jarvis.assistant.ui.components.JarvisOrb
import com.jarvis.assistant.ui.theme.JarvisColors

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    rmsLevel: Float,
    partialSpeech: String,
    onMicClick: () -> Unit,
    onStopClick: () -> Unit,
    onSendText: (String) -> Unit,
    onConfirmAction: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }

    val pending = uiState.pendingConfirmation
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { onConfirmAction(false) },
            containerColor = JarvisColors.PanelNavy,
            title = { Text("تأكيد تنفيذ الأمر", color = JarvisColors.TextPrimary) },
            text = {
                Text(
                    "هل توافق على تنفيذ الأمر (${pending.actionId}) بالبيانات: ${pending.parameters}؟",
                    color = JarvisColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { onConfirmAction(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.CyanCore)
                ) {
                    Text("موافق", color = JarvisColors.VoidBlack)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { onConfirmAction(false) }) {
                    Text("إلغاء", color = JarvisColors.TextPrimary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisColors.VoidBlack)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar with Status Telemetry & Settings Icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "J.A.R.V.I.S",
                    color = JarvisColors.CyanCore,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "${uiState.deviceInfo.manufacturer} ${uiState.deviceInfo.model} · Android ${uiState.deviceInfo.androidVersion}",
                    color = JarvisColors.TextSecondary,
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = JarvisColors.CyanCore
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Live Connectivity, Microphone, and Battery Status Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(JarvisColors.DeepNavy, RoundedCornerShape(12.dp))
                .border(1.dp, JarvisColors.HairlineBlue, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = if (uiState.connectivity.isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = if (uiState.connectivity.isOnline) JarvisColors.StatusNominal else JarvisColors.StatusWarning,
                    modifier = Modifier.size(16.dp)
                )
                Text(uiState.connectivity.transportLabel, color = JarvisColors.TextPrimary, fontSize = 12.sp)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = if (uiState.permissions.microphoneGranted) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = null,
                    tint = if (uiState.permissions.microphoneGranted) JarvisColors.CyanCore else JarvisColors.StatusError,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (uiState.permissions.microphoneGranted) "الميكروفون جاهز" else "صلاحية الميكروفون مطلوبة",
                    color = JarvisColors.TextPrimary,
                    fontSize = 12.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    imageVector = if (uiState.battery.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                    contentDescription = null,
                    tint = JarvisColors.CyanCore,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (uiState.battery.levelPercent >= 0) "${uiState.battery.levelPercent}%" else "--%",
                    color = JarvisColors.TextPrimary,
                    fontSize = 12.sp
                )
            }
        }

        if (!uiState.permissions.microphoneGranted) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.ElectricBlue),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("تفعيل صلاحية الميكروفون والإشعارات", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Large Animated JARVIS Avatar & Waveform
        JarvisOrb(
            state = uiState.assistantState,
            rmsLevel = rmsLevel
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "${uiState.assistantState.arabicLabel} (${uiState.assistantState.englishCode})",
            color = JarvisColors.CyanCore,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        if (uiState.assistantState == AssistantState.LISTENING && partialSpeech.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = partialSpeech,
                color = JarvisColors.TextPrimary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Last Command & Assistant Response Panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(JarvisColors.DeepNavy, RoundedCornerShape(14.dp))
                .border(1.dp, JarvisColors.HairlineBlue, RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (uiState.lastCommand.isNotBlank()) {
                Text("آخر أمر:", color = JarvisColors.TextSecondary, fontSize = 11.sp)
                Text(uiState.lastCommand, color = JarvisColors.TextPrimary, fontSize = 14.sp)
            }

            Text("رد جارفيس:", color = JarvisColors.CyanCore, fontSize = 11.sp)
            Text(uiState.lastResponse, color = JarvisColors.TextPrimary, fontSize = 15.sp, lineHeight = 22.sp)

            if (!uiState.errorDetail.isNullOrBlank()) {
                Text(
                    text = uiState.errorDetail,
                    color = JarvisColors.StatusError,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Manual Mic Button + Interruption Stop Control
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onMicClick,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.assistantState == AssistantState.LISTENING) {
                        JarvisColors.StatusError
                    } else {
                        JarvisColors.CyanCore
                    }
                ),
                modifier = Modifier.size(68.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "تحدث مع جارفيس",
                    tint = JarvisColors.VoidBlack,
                    modifier = Modifier.size(28.dp)
                )
            }

            if (uiState.assistantState == AssistantState.SPEAKING || uiState.assistantState == AssistantState.LISTENING) {
                OutlinedButton(
                    onClick = onStopClick,
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "إيقاف",
                        tint = JarvisColors.TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Text Command Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("اكتب أمرك بالمصري هنا...", color = JarvisColors.TextSecondary) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisColors.CyanCore,
                    unfocusedBorderColor = JarvisColors.HairlineBlue,
                    focusedTextColor = JarvisColors.TextPrimary,
                    unfocusedTextColor = JarvisColors.TextPrimary
                )
            )
            IconButton(
                onClick = {
                    val cmd = textInput.trim()
                    if (cmd.isNotBlank()) {
                        onSendText(cmd)
                        textInput = ""
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "إرسال",
                    tint = JarvisColors.CyanCore
                )
            }
        }
    }
}
