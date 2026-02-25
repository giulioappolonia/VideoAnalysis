package com.giulioapp.videoanalysis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
// NOTA: Qui ho cambiato SkiAnalysisTheme con VideoAnalysisTheme
import com.giulioapp.videoanalysis.ui.theme.VideoAnalysisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Usa il nome esatto del tema del tuo progetto
            VideoAnalysisTheme {
                MainScreen()
            }
        }
    }
}
