package com.samsungsync.datatransfer

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.ComponentName
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.samsungsync.datatransfer.network.VpnAutoService
import com.samsungsync.datatransfer.sandbox.IsolationService

class SecretVaultApplication : Application() {

    private var motorsInitialized = false
    private val TAG = "SecretVaultApp"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SecretVaultApplication onCreate: App starting - initializing motors automatically")

        if (!motorsInitialized) {
            synchronized(this) {
                if (!motorsInitialized) {
                    initMotors()
                    motorsInitialized = true
                }
            }
        } else {
            Log.d(TAG, "Motors already initialized - skipping re-initialization")
        }
    }

    private fun initMotors() {
        try {
            Log.d(TAG, "Initializing Zero-Leak Sandbox Core...")
            IsolationService.enqueue(this)

            Log.d(TAG, "Initializing Auto-Configuring VPN Service...")
            val vpnIntent = Intent(this, VpnAutoService::class.java)
            startService(vpnIntent)

            try {
                bindService(vpnIntent, object : ServiceConnection {
                    override fun onServiceConnected(className: ComponentName?, iBinder: IBinder?) {
                        Log.d(TAG, "VpnAutoService connected successfully")
                    }

                    override fun onServiceDisconnected(arg0: ComponentName?) {
                        Log.w(TAG, "VpnAutoService disconnected - will reconnect")
                        bindService(vpnIntent, this, Context.BIND_AUTO_CREATE)
                    }
                }, Context.BIND_AUTO_CREATE)
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot bind VPN service (may already be running or permission denied)")
            }

            Log.d(TAG, "Both motors initialized successfully - auto-running in background")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize motors", e)
        }
    }
}
