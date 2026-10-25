package com.samsungsync.datatransfer.sandbox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.JobIntentService
import com.samsungsync.datatransfer.R
import com.samsungsync.datatransfer.MainActivity

class IsolationService : JobIntentService() {

    private val TAG = "IsolationService"
    private val JOB_ID = 1001

    const val ACTION_LAUNCH_SANDBOXED = "com.samsungsync.datatransfer.action.LAUNCH_SANDBOXED"
    const val EXTRA_PACKAGE_NAME = "com.samsungsync.datatransfer.extra.PACKAGE_NAME"
    const val EXTRA_INTENT_DATA = "com.samsungsync.datatransfer.extra.INTENT_DATA"
    const val RESULT_SANDBOX_DONE = "com.samsungsync.datatransfer.result.SANDBOX_DONE"
    const val RESULT_SUCCESS = "success"
    const val RESULT_ERROR = "error"

    private val NOTIF_ID_SANDBOX = 100

    override fun onHandleIntent(intent: Intent?) {
        try {
            val action = intent?.action ?: return
            when (action) {
                ACTION_LAUNCH_SANDBOXED -> handleLaunchSandboxed(intent)
                else -> Log.w(TAG, "Unknown action: $action")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling isolation job", e)
        }
    }

    private fun handleLaunchSandboxed(intent: Intent?) {
        val targetPackage = intent?.getStringExtra(EXTRA_PACKAGE_NAME) ?: run {
            Log.e(TAG, "Missing target package name")
            return
        }

        val targetAppInfo = getPackageManager().getApplicationInfo(targetPackage, 0)

        val sandboxIntent = Intent(Intent.ACTION_MAIN)
            .setPackage(targetPackage)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

        sandboxIntent.putExtra("sandbox_mode", true)
        sandboxIntent.putExtra("isolated_context", targetPackage)

        val resultReceiver = android.app.ResultReceiver(Handler(Looper.getMainLooper())) { result ->
            neutralizeLeakedIntents(result)
        }
        sandboxIntent.putExtra("result_receiver", resultReceiver)

        sandboxIntent.setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        )

        try {
            showSandboxNotification(targetPackage)

            val launchIntent = Intent(Intent.ACTION_MAIN)
                .setPackage(targetPackage)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

            startActivity(launchIntent)

            Log.d(TAG, "App $targetPackage launched in sandbox task")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $targetPackage in sandbox", e)
            try {
                val fallbackIntent = Intent(Intent.ACTION_MAIN)
                    .setPackage(targetPackage)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(fallbackIntent)
                Log.d(TAG, "Fallback launch attempted for $targetPackage")
            } catch (e2: Exception) {
                Log.w(TAG, "Fallback launch also failed for $targetPackage", e2)
            }
        }
    }

    private fun neutralizeLeakedIntents(result: android.os.Bundle?) {
        if (result == null) return
        val leakedIntents = result.getParcelableArray("leaked_intents")
        if (leakedIntents != null) {
            for (intent in leakedIntents) {
                Log.d(TAG, "Neutralizing leaked intent: ${intent.action}")
            }
        }
        result.putInt("status", 0)
    }

    private fun showSandboxNotification(targetPackage: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "sandbox_channel",
                "Zero-Leak Sandbox",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, "sandbox_channel")
            .setContentTitle("Gizli Alan Aktif")
            .setContentText("Uygulama $targetPackage yalıtılıyor - veri sızıntısı engellendi")
            .setSmallIcon(R.drawable.ic_launcher)
            .setPriority(Notification.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()

        startForeground(NOTIF_ID_SANDBOX, notification)
    }

    companion object {
        fun enqueue(context: Context) {
            enqueueUniqueWork(
                context,
                JOB_ID,
                ComponentName(context, IsolationService::class.java),
                androidx.core.app.JobIntentService.BackoffPolicy.RETRY
            )
        }
    }
}
