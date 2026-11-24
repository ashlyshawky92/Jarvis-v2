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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
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
import com.jarvis.assistant.core.model.TurnRole
import com.jarvis.assistant.permissions.PermissionRationale
import com.jarvis.assistant.ui.DashboardUiState
import com.jarvis.assistant.ui.components.JarvisOrb
import com.jarvis.assistant.ui.theme.JarvisColors

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    rmsLevel: Float,
    partialSpeech: String,
    rationales: List<PermissionRationale>,
    onMicClick: () -> Unit,
    onStopClick: () -> Unit,
    onSendText: (String) -> Unit,
    onConfirmAction: (Boolean) -> Unit,
    onDeleteTurn: (String) -> Unit,
    onClearHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    var showHistoryPanel by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }

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

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            containerColor = JarvisColors.PanelNavy,
            title = { Text("لماذا نطلب هذه الصلاحيات؟", color = JarvisColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rationales.forEach { item ->
                        Text("${item.titleArabic}: ${item.explanationArabic}", color = JarvisColors.TextSecondary, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        onRequestPermissions()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.CyanCore)
                ) {
                    Text("متابعة", color = JarvisColors.VoidBlack)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPermissionDialog = false }) {
                    Text("لاحقاً", color = JarvisColors.TextPrimary)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
            Row {
                IconButton(onClick = { showHistoryPanel = !showHistoryPanel }) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "سجل المحادثات",
                        tint = if (showHistoryPanel) JarvisColors.CyanCore else JarvisColors.TextSecondary
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "الإعدادات",
                        tint = JarvisColors.CyanCore
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

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
                onClick = { showPermissionDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.ElectricBlue),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("مراجعة وتفعيل صلاحية الميكروفون والإشعارات", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        JarvisOrb(state = uiState.assistantState, rmsLevel = rmsLevel)

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

        Spacer(modifier = Modifier.height(16.dp))

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
                Text(text = uiState.errorDetail, color = JarvisColors.StatusError, fontSize = 12.sp)
            }
        }

        if (showHistoryPanel) {
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JarvisColors.DeepNavy, RoundedCornerShape(14.dp))
                    .border(1.dp, JarvisColors.HairlineBlue, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("سجل المحادثات المحفوظ محلياً (${uiState.conversation.size})", color = JarvisColors.CyanCore, fontSize = 12.sp)
                    if (uiState.conversation.isNotEmpty()) {
                        OutlinedButton(onClick = onClearHistory) {
                            Text("مسح السجل", color = JarvisColors.StatusError, fontSize = 11.sp)
                        }
                    }
                }
                if (uiState.conversation.isEmpty()) {
                    Text("لا يوجد سجل محادثات حالياً.", color = JarvisColors.TextSecondary, fontSize = 12.sp)
                } else {
                    uiState.conversation.takeLast(12).forEach { turn ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(JarvisColors.PanelNavy, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (turn.role == TurnRole.USER) "أنت" else "جارفيس",
                                    color = JarvisColors.CyanCore,
                                    fontSize = 10.sp
                                )
                                Text(turn.text, color = JarvisColors.TextPrimary, fontSize = 12.sp)
                            }
                            IconButton(onClick = { onDeleteTurn(turn.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = JarvisColors.TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                modifier = Modifier.size(66.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "تحدث مع جارفيس",
                    tint = JarvisColors.VoidBlack,
                    modifier = Modifier.size(28.dp)
                )
            }

            if (
                uiState.assistantState == AssistantState.SPEAKING ||
                uiState.assistantState == AssistantState.LISTENING ||
                uiState.assistantState == AssistantState.THINKING
            ) {
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

        Spacer(modifier = Modifier.height(16.dp))

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
