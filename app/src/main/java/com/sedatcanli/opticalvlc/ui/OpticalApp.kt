package com.sedatcanli.opticalvlc.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sedatcanli.opticalvlc.ui.components.StatusPill
import com.sedatcanli.opticalvlc.ui.theme.Ink
import com.sedatcanli.opticalvlc.ui.theme.NeonCyan
import com.sedatcanli.opticalvlc.ui.theme.OutlineDim
import com.sedatcanli.opticalvlc.ui.theme.TextMuted
import com.sedatcanli.opticalvlc.ui.theme.TextPrimary

@Composable
fun OpticalApp(
    transmitterViewModel: TransmitterViewModel,
    receiverViewModel: ReceiverViewModel
) {
    Scaffold(containerColor = Ink) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "BEAMTALK",
                        style = MaterialTheme.typography.displaySmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "IŞIKLA İLETİŞİM · LED ⇄ KAMERA",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.weight(1f))
                StatusPill(text = "İNTERNET YOK", color = NeonCyan)
            }

            HorizontalDivider(color = OutlineDim)

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VERİCİ · TX",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan
                    )
                    Spacer(Modifier.weight(1f))
                    StatusPill(text = "ÖN PLAN", color = TextMuted)
                }
                TransmitterTab(transmitterViewModel)
            }

            HorizontalDivider(color = OutlineDim)

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ALICI · RX",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan
                    )
                    Spacer(Modifier.weight(1f))
                    StatusPill(text = "ÖN PLAN", color = TextMuted)
                }
                ReceiverTab(receiverViewModel)
            }
        }
    }
}
