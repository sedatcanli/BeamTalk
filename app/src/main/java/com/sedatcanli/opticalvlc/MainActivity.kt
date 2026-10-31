package com.sedatcanli.opticalvlc

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sedatcanli.opticalvlc.ui.OpticalApp
import com.sedatcanli.opticalvlc.ui.ReceiverViewModel
import com.sedatcanli.opticalvlc.ui.TransmitterViewModel
import com.sedatcanli.opticalvlc.ui.theme.OpticalVlcTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            OpticalVlcTheme {
                val transmitterViewModel: TransmitterViewModel = viewModel()
                val receiverViewModel: ReceiverViewModel = viewModel()
                OpticalApp(
                    transmitterViewModel = transmitterViewModel,
                    receiverViewModel = receiverViewModel
                )
            }
        }
    }
}
