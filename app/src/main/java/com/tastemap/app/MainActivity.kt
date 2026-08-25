package com.tastemap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.tastemap.app.ui.nav.TasteMapNav
import com.tastemap.app.ui.theme.TasteMapTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // F15：从外部分享进入时，预填的记录信息经 extras 传给导航
        val pendingName = intent?.getStringExtra(EXTRA_RECORD_NAME)
        val pendingLat = intent?.getDoubleExtra(EXTRA_RECORD_LAT, Double.NaN)?.takeIf { !it.isNaN() }
        val pendingLng = intent?.getDoubleExtra(EXTRA_RECORD_LNG, Double.NaN)?.takeIf { !it.isNaN() }
        val initialRecord = if (pendingLat != null && pendingLng != null) {
            com.tastemap.app.ui.nav.PendingRecord(pendingLat, pendingLng, pendingName)
        } else null

        setContent {
            TasteMapTheme {
                TasteMapNav(initialRecord = initialRecord)
            }
        }
    }

    companion object {
        const val EXTRA_RECORD_NAME = "record_name"
        const val EXTRA_RECORD_LAT = "record_lat"
        const val EXTRA_RECORD_LNG = "record_lng"
    }
}
