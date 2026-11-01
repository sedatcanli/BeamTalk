package com.sedatcanli.opticalvlc.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sedatcanli.opticalvlc.core.FrameCodec
import com.sedatcanli.opticalvlc.core.Language
import com.sedatcanli.opticalvlc.speech.VoskSttEngine
import com.sedatcanli.opticalvlc.tx.TorchTransmitter
import com.sedatcanli.opticalvlc.ui.components.LanguageSelector
import com.sedatcanli.opticalvlc.ui.components.SectionCard
import com.sedatcanli.opticalvlc.ui.components.StatusPill
import com.sedatcanli.opticalvlc.ui.theme.Ink
import com.sedatcanli.opticalvlc.ui.theme.NeonCyan
import com.sedatcanli.opticalvlc.ui.theme.OutlineDim
import com.sedatcanli.opticalvlc.ui.theme.SignalAmber
import com.sedatcanli.opticalvlc.ui.theme.SignalGreen
import com.sedatcanli.opticalvlc.ui.theme.SignalRed
import com.sedatcanli.opticalvlc.ui.theme.TextMuted
import com.sedatcanli.opticalvlc.ui.theme.TextPrimary
import java.util.Locale

@Composable
fun TransmitterTab(vm: TransmitterViewModel) {
    val tx by vm.txState.collectAsStateWithLifecycle()
    val stt by vm.sttState.collectAsStateWithLifecycle()

    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micPermission.value = granted
        if (granted) {
            vm.startListening()
        } else {
            vm.showMessage("STT iÃ§in mikrofon izni gerekli")
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermission.value = granted
        if (granted) {
            vm.refreshTorchAvailability()
            vm.startTransmission()
        } else {
            vm.showMessage("Flaş denetimi için kamera izni gerekli")
        }
    }

    val listening = stt.status == VoskSttEngine.Status.LISTENING ||
        stt.status == VoskSttEngine.Status.LOADING

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    text = if (vm.torchAvailable) "FLAÅž HAZIR" else "FLAÅž YOK",
                    color = if (vm.torchAvailable) SignalGreen else SignalRed
                )
                Spacer(Modifier.width(8.dp))
                StatusPill(text = "VOSK Â· OFFLINE", color = NeonCyan)
                Spacer(Modifier.weight(1f))
                StatusPill(
                    text = "${vm.bitRateHz.toInt()} Hz",
                    color = SignalAmber
                )
            }
        }

        item {
            SectionCard(
                title = "Kaynak Dil (STT)",
                trailing = {
                    when (stt.status) {
                        VoskSttEngine.Status.LISTENING -> StatusPill("DÄ°NLÄ°YOR", SignalGreen)
                        VoskSttEngine.Status.LOADING -> StatusPill("MODEL", SignalAmber)
                        VoskSttEngine.Status.ERROR -> StatusPill("HATA", SignalRed)
                        VoskSttEngine.Status.IDLE -> Unit
                    }
                }
            ) {
                LanguageSelector(
                    label = "KonuÅŸma dili",
                    selected = vm.sourceLanguage,
                    options = Language.sttLanguages,
                    onSelect = vm::onSourceLanguageSelected
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Ses tanÄ±ma tamamen cihaz iÃ§inde Ã§alÄ±ÅŸÄ±r; model ilk kullanÄ±mda " +
                        "assets klasÃ¶rÃ¼nden cihaza aÃ§Ä±lÄ±r.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                if (stt.status == VoskSttEngine.Status.LOADING) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = NeonCyan,
                        trackColor = OutlineDim
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Model yÃ¼kleniyorâ€¦ (ilk seferde birkaÃ§ saniye)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SignalAmber
                    )
                }
                stt.error?.let { error ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SignalRed
                    )
                }
            }
        }

        item {
            SectionCard(title = "Mesaj") {
                OutlinedTextField(
                    value = vm.text,
                    onValueChange = vm::onTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = {
                        Text("KonuÅŸun veya mesajÄ± buraya yazÄ±nâ€¦", color = TextMuted)
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    isError = vm.payloadBytes > FrameCodec.MAX_PAYLOAD_BYTES,
                    supportingText = {
                        Text(
                            text = "${vm.payloadBytes} / ${FrameCodec.MAX_PAYLOAD_BYTES} bayt  Â·  " +
                                "${vm.text.length} karakter",
                            color = if (vm.payloadBytes > FrameCodec.MAX_PAYLOAD_BYTES) {
                                SignalRed
                            } else {
                                TextMuted
                            }
                        )
                    }
                )

                if (stt.partialText.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "â€¦ ${stt.partialText}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonCyan
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            when {
                                listening -> vm.stopListening()
                                micPermission.value -> vm.startListening()
                                else -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = when (stt.status) {
                                VoskSttEngine.Status.LOADING -> "Model yÃ¼kleniyorâ€¦ (durdur)"
                                VoskSttEngine.Status.LISTENING -> "Dinlemeyi durdur"
                                else -> "KonuÅŸ / Dinle"
                            }
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (listening) vm.stopListening()
                            vm.clearText()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Temizle")
                    }
                }
            }
        }

        item {
            SectionCard(title = "Fiziksel Katman") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Bit hÄ±zÄ±",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${vm.bitRateHz.toInt()} Hz",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan
                    )
                }
                Slider(
                    value = vm.bitRateHz,
                    onValueChange = vm::onBitRateChange,
                    valueRange = TorchTransmitter.MIN_BIT_RATE_HZ.toFloat()..
                        TorchTransmitter.MAX_BIT_RATE_HZ.toFloat(),
                    steps = 15
                )
                Text(
                    text = "Manchester sembol hÄ±zÄ±: ${(vm.bitRateHz * 2).toInt()} chip/sn  Â·  " +
                        "2 chip = 1 bit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                if (vm.bitRateHz > 10f) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Kamera 30 FPS: 11–30 Hz'de hata oranı artar. Menzil için 2–10 Hz önerilir.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SignalAmber
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Tekrar",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { vm.onRepeatsChange(vm.repeats - 1) },
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("âˆ’")
                    }
                    Text(
                        text = vm.repeats.toString(),
                        modifier = Modifier.padding(horizontal = 14.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = SignalAmber
                    )
                    OutlinedButton(
                        onClick = { vm.onRepeatsChange(vm.repeats + 1) },
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("+")
                    }
                }

                Spacer(Modifier.height(12.dp))
                val frameBytes = FrameCodec.HEADER_SIZE + vm.payloadBytes.coerceAtLeast(1) +
                    FrameCodec.TRAILER_SIZE
                val bitCount = frameBytes * 8
                Text(
                    text = "Ã‡erÃ§eve: 0xAA | dil | boy | ${vm.payloadBytes} B | CRC-8\n" +
                        "$frameBytes bayt Â· $bitCount bit Â· ${bitCount * 2} chip / tekrar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Tahmini sÃ¼re: ${formatDuration(vm.estimatedDurationMs)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = SignalAmber
                )
            }
        }

        item {
            Button(
                onClick = {
                    when {
                        tx.transmitting -> vm.cancelTransmission()
                        !vm.torchAvailable -> vm.showMessage(
                            "Bu cihazda kullanÄ±labilir flaÅŸ bulunamadÄ±"
                        )
                        !cameraPermission.value -> cameraLauncher.launch(Manifest.permission.CAMERA)
                        else -> vm.startTransmission()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = tx.transmitting || (vm.canTransmit && vm.torchAvailable),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tx.transmitting) SignalRed else NeonCyan,
                    contentColor = Ink,
                    disabledContainerColor = OutlineDim,
                    disabledContentColor = TextMuted
                )
            ) {
                Text(
                    text = if (tx.transmitting) {
                        "Ä°LETÄ°MÄ° DURDUR"
                    } else {
                        "OPTÄ°K Ä°LETÄ°MÄ° BAÅžLAT"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }

            if (tx.transmitting) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { tx.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = NeonCyan,
                    trackColor = OutlineDim
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = String.format(
                        Locale.US,
                        "GÃ¶nderiliyorâ€¦ %%%d  Â·  tekrar %d/%d",
                        (tx.progress * 100).toInt(),
                        tx.repeatIndex.coerceAtLeast(1),
                        tx.repeats
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }

            vm.uiMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SignalRed
                )
            }
        }

        item {
            SectionCard(title = "NasÄ±l Ã‡alÄ±ÅŸÄ±r?") {
                Text(
                    text = "â€¢ Verici telefonun LED flaÅŸÄ±, alÄ±cÄ± telefonun arka kamerasÄ±na " +
                        "Manchester kodlu Ä±ÅŸÄ±k darbeleri gÃ¶nderir.\n" +
                        "â€¢ 5â€“10 m menzil iÃ§in alÄ±cÄ±da minimum pozlama ve karanlÄ±k ortam " +
                        "gerekir.\n" +
                        "â€¢ Paket: 0xAA preamble, dil kimliÄŸi, uzunluk, UTF-8 metin ve CRC-8.\n" +
                        "â€¢ Uzun mesajlar dÃ¼ÅŸÃ¼k bit hÄ±zÄ±nda dakikalar sÃ¼rebilir; kÄ±sa tutun.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        }
    }
}


