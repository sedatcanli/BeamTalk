package com.sedatcanli.opticalvlc.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sedatcanli.opticalvlc.ui.components.StatusPill
import com.sedatcanli.opticalvlc.ui.theme.Ink
import com.sedatcanli.opticalvlc.ui.theme.InkRaised
import com.sedatcanli.opticalvlc.ui.theme.NeonCyan
import com.sedatcanli.opticalvlc.ui.theme.TextMuted
import com.sedatcanli.opticalvlc.ui.theme.TextPrimary

@Composable
fun OpticalApp(
    transmitterViewModel: TransmitterViewModel,
    receiverViewModel: ReceiverViewModel
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(containerColor = Ink) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "OPTICAL VLC",
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

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = InkRaised,
                contentColor = NeonCyan
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "VERİCİ · TX",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "ALICI · RX",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                )
            }

            when (selectedTab) {
                0 -> TransmitterTab(transmitterViewModel)
                else -> ReceiverTab(receiverViewModel)
            }
        }
    }
}
