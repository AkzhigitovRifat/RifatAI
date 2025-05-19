package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "Будильник сработал!")

        val message = intent.getStringExtra("ALARM_MESSAGE") ?: "Время будильника!"
        val alarmId = intent.getIntExtra("ALARM_ID", 0)

        // Создаем intent для открытия приложения при нажатии на уведомление
        val notificationIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Получаем стандартный звук будильника
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Создаем менеджер уведомлений
        val notificationManager = NotificationManagerCompat.from(context)

        // Для Android 8.0+ требуется создавать канал уведомлений
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "alarm_channel",
                "Будильники",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Канал для уведомлений будильника"
                enableVibration(true)
            }
            val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            systemNotificationManager.createNotificationChannel(channel)
        }

        // Создаем уведомление
        val notification = NotificationCompat.Builder(context, "alarm_channel")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Будильник")
            .setContentText(message)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // Проверяем разрешение для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Проверка разрешения
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED) {
                // Показываем уведомление только если есть разрешение
                notificationManager.notify(alarmId, notification)
            } else {
                // Если разрешения нет, выводим в лог
                Log.e("AlarmReceiver", "Нет разрешения POST_NOTIFICATIONS для показа уведомления будильника")
                // Альтернативный способ оповещения - можно сделать, например, Activity с полноэкранным будильником
                val alarmActivityIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("ALARM_TRIGGERED", true)
                    putExtra("ALARM_MESSAGE", message)
                }
                context.startActivity(alarmActivityIntent)
            }
        } else {
            // Для более старых версий Android разрешение не требуется
            notificationManager.notify(alarmId, notification)
        }
    }
}
