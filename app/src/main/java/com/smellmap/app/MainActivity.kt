package com.smellmap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.smellmap.app.ui.MapHomeScreen
import com.smellmap.app.ui.theme.TasteMapTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TasteMapTheme {
                MapHomeScreen()
            }
        }
    }
}
