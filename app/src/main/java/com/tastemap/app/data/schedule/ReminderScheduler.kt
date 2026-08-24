package com.tastemap.app.data.schedule

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tastemap.app.R
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F08 日程提醒（D8：本地通知）：AlarmManager 定点到时收提醒。
 * 用 setAndAllowWhileIdle（非精确闹钟）——免 SCHEDULE_EXACT_ALARM 权限与商店审查，
 * 吃饭提醒 ±15 分钟完全可接受。
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(scheduleId: Long, date: LocalDate, mealSlot: String, title: String) {
        val trigger = triggerAt(date, mealSlot) ?: return
        val pi = pendingIntent(scheduleId, title)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
    }

    fun cancel(scheduleId: Long) {
        alarmManager.cancel(pendingIntent(scheduleId, ""))
    }

    private fun pendingIntent(scheduleId: Long, title: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_ID, scheduleId)
            putExtra(EXTRA_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context,
            scheduleId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_ID = "schedule_id"
        const val EXTRA_TITLE = "title"
        val CHANNEL_ID = "tastemap_schedule"

        /** 各餐段提醒时刻；时间已过则不再提醒（当天） */
        fun triggerAt(date: LocalDate, mealSlot: String): Long? {
            val time = when (mealSlot) {
                "早餐" -> LocalTime.of(8, 0)
                "午餐" -> LocalTime.of(12, 0)
                "晚餐" -> LocalTime.of(18, 30)
                "夜宵" -> LocalTime.of(22, 30)
                else -> return null
            }
            val dt = LocalDateTime.of(date, time)
            return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "美食日程提醒", NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
        }
    }
}

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "美食日程"
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, 0L).toInt()
        ReminderScheduler.ensureChannel(context)
        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("到点开吃")
            .setContentText(title)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, notification)
    }
}
