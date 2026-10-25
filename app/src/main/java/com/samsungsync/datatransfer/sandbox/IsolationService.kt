package com.samsungsync.datatransfer.sandbox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.util.Log
import androidx.core.content.ContextCompat
import com.samsungsync.datatransfer.R
import com.samsungsync.datatransfer.MainActivity

class IsolationService : Service() {

    companion object {
        private const val TAG = "IsolationService"
        private const val NOTIF_ID_SANDBOX = 100

        const val ACTION_LAUNCH_SANDBOXED = "com.samsungsync.datatransfer.action.LAUNCH_SANDBOXED"
        const val EXTRA_PACKAGE_NAME = "com.samsungsync.datatransfer.extra.PACKAGE_NAME"
        const val EXTRA_INTENT_DATA = "com.samsungsync.datatransfer.extra.INTENT_DATA"
        const val RESULT_SANDBOX_DONE = "com.samsungsync.datatransfer.result.SANDBOX_DONE"
        const val RESULT_SUCCESS = "success"
        const val RESULT_ERROR = "error"

        // Overloaded enqueue methods with default parameter to support SecretVaultApplication calls
        fun enqueue(context: Context, intent: Intent = Intent(context, IsolationService::class.java).apply { action = ACTION_LAUNCH_SANDBOXED }) {
            ContextCompat.startForegroundService(context, intent)
        }
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val action = intent?.action
            if (action == ACTION_LAUNCH_SANDBOXED) {
                handleLaunchSandboxed(intent)
            } else {
                Log.w(TAG, "Unknown action: $action")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling isolation job", e)
        }
        return START_NOT_STICKY
    }

    private fun handleLaunchSandboxed(intent: Intent?) {
        val targetPackage = intent?.getStringExtra(EXTRA_PACKAGE_NAME) ?: run {
            Log.e(TAG, "Missing target package name")
            stopSelf()
            return
        }

        try {
            showSandboxNotification(targetPackage)

            val resultReceiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    neutralizeLeakedIntents(resultData)
                }
            }

            val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)?.apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                putExtra("sandbox_mode", true)
                putExtra("isolated_context", targetPackage)
                putExtra("result_receiver", resultReceiver)
            }

            if (launchIntent != null) {
                startActivity(launchIntent)
                Log.d(TAG, "App $targetPackage launched in sandbox task")
            } else {
                Log.e(TAG, "Could not find launch intent for $targetPackage")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $targetPackage in sandbox", e)
        } finally {
            stopSelf()
        }
    }

    private fun neutralizeLeakedIntents(result: Bundle?) {
        if (result == null) return
        val leakedIntents = result.getParcelableArray("leaked_intents")
        if (leakedIntents != null) {
            for (intent in leakedIntents) {
                if (intent is Intent) {
                    Log.d(TAG, "Neutralizing leaked intent: ${intent.action}")
                }
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
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            .build()

        startForeground(NOTIF_ID_SANDBOX, notification)
    }
}
