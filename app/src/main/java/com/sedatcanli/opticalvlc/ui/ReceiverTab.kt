package com.sedatcanli.opticalvlc.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sedatcanli.opticalvlc.core.Language
import com.sedatcanli.opticalvlc.rx.CameraSupport
import com.sedatcanli.opticalvlc.rx.OpticalDemodulator
import com.sedatcanli.opticalvlc.speech.OfflineTtsEngine
import com.sedatcanli.opticalvlc.ui.components.LanguageSelector
import com.sedatcanli.opticalvlc.ui.components.ReceiverCameraBox
import com.sedatcanli.opticalvlc.ui.components.SectionCard
import com.sedatcanli.opticalvlc.ui.components.StatusPill
import com.sedatcanli.opticalvlc.ui.theme.InkRaised
import com.sedatcanli.opticalvlc.ui.theme.NeonCyan
import com.sedatcanli.opticalvlc.ui.theme.OutlineDim
import com.sedatcanli.opticalvlc.ui.theme.SignalAmber
import com.sedatcanli.opticalvlc.ui.theme.SignalGreen
import com.sedatcanli.opticalvlc.ui.theme.SignalRed
import com.sedatcanli.opticalvlc.ui.theme.TextMuted
import com.sedatcanli.opticalvlc.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun ReceiverTab(vm: ReceiverViewModel) {
    val context = LocalContext.current
    val rx by vm.rxState.collectAsStateWithLifecycle()
    val tts by vm.ttsState.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val lumaHistory by vm.lumaHistory.collectAsStateWithLifecycle()
    val overlay by vm.overlay.collectAsStateWithLifecycle()
    val statusMessage by vm.statusMessage.collectAsStateWithLifecycle()
    val cameraError by vm.cameraError.collectAsStateWithLifecycle()

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermission.value = granted
        if (!granted) {
            vm.reportCameraError("Kamera izni verilmedi")
        }
    }

    var minExposure by rememberSaveable { mutableStateOf(true) }
    var aeLock by rememberSaveable { mutableStateOf(false) }
    var telephoto by rememberSaveable { mutableStateOf(false) }
    var cameraRestartKey by remember { mutableIntStateOf(0) }
    var capabilities by remember { mutableStateOf<CameraSupport.Info?>(null) }

    LaunchedEffect(Unit) {
        capabilities = withContext(Dispatchers.IO) {
            CameraSupport.probe(context)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            if (!cameraPermission.value) {
                SectionCard(title = "Kamera") {
                    Text(
                        text = "AlÄ±cÄ± modu, gÃ¶ndericinin flaÅŸÄ±nÄ± yakalamak iÃ§in arka kamerayÄ± " +
                            "640Ã—480 YUV akÄ±ÅŸÄ±nda kullanÄ±r. Ä°nternet izni gerekmez.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Kamera iznini ver")
                    }
                }
            } else {
                ReceiverCameraBox(
                    hasPermission = true,
                    telephoto = telephoto && capabilities?.hasTelephoto == true,
                    minExposure = minExposure,
                    aeLock = aeLock,
                    minExposureCompensation = capabilities?.minExposureCompensation ?: 0,
                    overlayInfo = overlay,
                    phase = rx.phase,
                    onFrame = vm::onLumaFrame,
                    onError = vm::reportCameraError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f),
                    restartKey = cameraRestartKey
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    text = phaseLabel(rx.phase),
                    color = phaseColor(rx.phase)
                )
                Spacer(Modifier.weight(1f))
                StatusPill(
                    text = if (rx.burstActive) "BURST ${rx.burstDurationMs / 1000.0}s" else "IDLE",
                    color = if (rx.burstActive) SignalAmber else TextMuted
                )
            }
        }

        item {
            SectionCard(title = "CanlÄ± Sinyal (ROI Luma)") {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Metric("LUMA", "${rx.luma.toInt()}", NeonCyan)
                    Metric("EÅžÄ°K", "${rx.threshold.toInt()}", SignalAmber)
                    Metric("KONTRAST", "${rx.contrast.toInt()}", SignalGreen)
                }
                Spacer(Modifier.height(10.dp))
                com.sedatcanli.opticalvlc.ui.components.LumaSparkline(
                    values = lumaHistory,
                    threshold = rx.threshold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Turuncu kesikli Ã§izgi uyarlanabilir eÅŸik; parlak nokta ROI " +
                        "ortalamasÄ± 30 FPS Ã¶rneklenir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        }

        item {
            SectionCard(
                title = "Seslendirme (TTS)",
                trailing = {
                    StatusPill(
                        text = if (tts.speaking) "KONUÅžUYOR" else "HAZIR",
                        color = if (tts.speaking) SignalAmber else NeonCyan
                    )
                }
            ) {
                LanguageSelector(
                    label = "Hedef dil",
                    selected = vm.targetLanguage,
                    options = Language.all,
                    onSelect = vm::onTargetLanguageSelected
                )
                Spacer(Modifier.height(10.dp))
                SwitchRow(
                    label = "Otomatik seslendir",
                    description = "AlÄ±nan her paket hoparlÃ¶rden okunur",
                    checked = vm.autoSpeak,
                    onCheckedChange = vm::onAutoSpeakChange
                )
                SwitchRow(
                    label = "LANG_ID dilini kullan",
                    description = "GÃ¶ndericinin seÃ§tiÄŸi dil, hedef dil yerine kullanÄ±lÄ±r",
                    checked = vm.useFrameLanguage,
                    onCheckedChange = vm::onUseFrameLanguageChange
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = ttsStatusText(tts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ttsStatusColor(tts)
                )
                if (tts.speaking) {
                    TextButton(onClick = vm::stopSpeaking) {
                        Text("Seslendirmeyi durdur")
                    }
                }
            }
        }

        item {
            SectionCard(title = "Kamera AyarlarÄ±") {
                SwitchRow(
                    label = "Minimum pozlama (uzun menzil)",
                    description = "AE telafisi en dÃ¼ÅŸÃ¼k deÄŸere, odak sonsuza kilitlenir",
                    checked = minExposure,
                    onCheckedChange = { minExposure = it }
                )
                SwitchRow(
                    label = "AE kilidi",
                    description = "PozlamayÄ± sabitler; flaÅŸ arka plandan ayrÄ±ÅŸÄ±r",
                    checked = aeLock,
                    onCheckedChange = { aeLock = it }
                )
                SwitchRow(
                    label = "Telefoto kamera",
                    description = if (capabilities?.hasTelephoto == true) {
                        "Daha uzun odak uzaklÄ±ÄŸÄ± ile menzili artÄ±rÄ±r"
                    } else {
                        "Bu cihazda telefoto kamera tespit edilmedi"
                    },
                    checked = telephoto && capabilities?.hasTelephoto == true,
                    enabled = capabilities?.hasTelephoto == true,
                    onCheckedChange = { telephoto = it }
                )
                capabilities?.let { info ->
                    Spacer(Modifier.height(4.dp))
                    val exposureText = if (info.minExposureCompensation == 0 &&
                        info.maxExposureCompensation == 0
                    ) {
                        "Pozlama telafisi desteklenmiyor"
                    } else {
                        "Pozlama telafisi aralÄ±ÄŸÄ±: ${info.minExposureCompensation} â€¦ " +
                            "${info.maxExposureCompensation}"
                    }
                    Text(
                        text = exposureText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }
        }

        cameraError?.let { error ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SignalRed.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, SignalRed.copy(alpha = 0.4f))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SignalRed
                        )
                        TextButton(
                            onClick = {
                                vm.clearCameraError()
                                cameraRestartKey++
                            }
                        ) {
                            Text("KamerayÄ± yeniden dene")
                        }
                    }
                }
            }
        }

        statusMessage?.let { message ->
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SignalAmber,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ALINAN MESAJLAR (${messages.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = NeonCyan
                )
                Spacer(Modifier.weight(1f))
                if (messages.isNotEmpty()) {
                    TextButton(onClick = vm::clearMessages) {
                        Text("Temizle")
                    }
                }
            }
        }

        if (messages.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = InkRaised)
                ) {
                    Text(
                        text = "HenÃ¼z paket yok. Verici telefonun flaÅŸÄ±nÄ± bu kameraya doÄŸrultun; " +
                            "alÄ±cÄ±da minimum pozlama aÃ§Ä±k olsun. KaranlÄ±k ortam menzili artÄ±rÄ±r.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(messages, key = { it.id }) { message ->
                MessageCard(
                    message = message,
                    onSpeak = { vm.speakMessage(message) }
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Metric(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) TextPrimary else TextMuted
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun MessageCard(
    message: ReceivedMessage,
    onSpeak: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = InkRaised),
        border = BorderStroke(1.dp, OutlineDim)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    text = message.language?.displayName
                        ?: "Dil #${message.languageId}",
                    color = NeonCyan
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatClock(message.receivedAtMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "CRC âœ“",
                    style = MaterialTheme.typography.labelSmall,
                    color = SignalGreen
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = String.format(
                        Locale.US,
                        "%.1f Hz Â· kontrast %.0f Â· %s",
                        message.bitRateHz,
                        message.contrast,
                        formatDuration(message.durationMs)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSpeak) {
                    Text("Seslendir")
                }
            }
        }
    }
}

private fun ttsStatusText(state: OfflineTtsEngine.State): String = when {
    !state.initialized -> "TTS motoru baÅŸlatÄ±lÄ±yorâ€¦"
    !state.languageAvailable ->
        "${state.language?.displayName ?: "Hedef dil"} iÃ§in ses paketi yok"
    state.embeddedVoiceAvailable -> "GÃ¶mÃ¼lÃ¼ (Ã§evrimdÄ±ÅŸÄ±) ses paketi hazÄ±r"
    else -> "TTS hazÄ±r (gÃ¶mÃ¼lÃ¼ ses paketi bildirilmiyor)"
}

private fun ttsStatusColor(state: OfflineTtsEngine.State): androidx.compose.ui.graphics.Color =
    when {
        !state.initialized -> TextMuted
        !state.languageAvailable -> SignalRed
        state.embeddedVoiceAvailable -> SignalGreen
        else -> SignalAmber
    }

private fun phaseLabel(phase: OpticalDemodulator.RxPhase): String = when (phase) {
    OpticalDemodulator.RxPhase.IDLE -> "SÄ°NYAL BEKLENÄ°YOR"
    OpticalDemodulator.RxPhase.PREAMBLE_ACQUISITION -> "PREAMBLE ARANIYOR"
    OpticalDemodulator.RxPhase.READ_HEADER_AND_BODY -> "VERÄ° OKUNUYOR"
    OpticalDemodulator.RxPhase.CRC_CHECK -> "CRC DENETLENÄ°YOR"
    OpticalDemodulator.RxPhase.DECODED -> "PAKET ALINDI"
    OpticalDemodulator.RxPhase.ERROR -> "PAKET HATALI"
}

private fun phaseColor(phase: OpticalDemodulator.RxPhase): androidx.compose.ui.graphics.Color =
    when (phase) {
        OpticalDemodulator.RxPhase.IDLE -> NeonCyan
        OpticalDemodulator.RxPhase.PREAMBLE_ACQUISITION,
        OpticalDemodulator.RxPhase.READ_HEADER_AND_BODY,
        OpticalDemodulator.RxPhase.CRC_CHECK -> SignalAmber

        OpticalDemodulator.RxPhase.DECODED -> SignalGreen
        OpticalDemodulator.RxPhase.ERROR -> SignalRed
    }

