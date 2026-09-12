package com.meteoanalyst.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.meteoanalyst.app.ui.main.MainScreen
import com.meteoanalyst.app.ui.theme.MeteoAnalystTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeteoAnalystTheme {
                MainScreen()
            }
        }
    }
}
