package com.tastemap.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.tastemap.app.share.ShareTextParser

/**
 * F15 v1：系统分享目标。在点评/美团/小红书等 App 里点"分享→味觉地图"进入，
 * 解析文本中的店名/坐标后直接落到新建记录页。
 */
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val parsed = ShareTextParser.parse(text)

        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                parsed.name?.let { putExtra(MainActivity.EXTRA_RECORD_NAME, it) }
                parsed.lat?.let { putExtra(MainActivity.EXTRA_RECORD_LAT, it) }
                parsed.lng?.let { putExtra(MainActivity.EXTRA_RECORD_LNG, it) }
            },
        )
        finish()
    }
}
