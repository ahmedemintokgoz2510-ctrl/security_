package com.samsungsync.datatransfer

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.samsungsync.datatransfer.network.VpnAutoService
import com.samsungsync.datatransfer.sandbox.IsolationService

/**
 * SecretVault Application Class:
 - Auto-initializes the Zero-Leak Sandbox Core when the app is first created
 - Auto-starts the Auto-Configuring VPN Service without any user settings
 - Ensures both motorlar (izole konteyner ve ağ tüneli) %100 otomatik çalışır
 - PIN kilidi ve ayarlar son kullanıcıdan gizli, arka plandadır
 */
class SecretVaultApplication : Application() {

    // Track whether the motors have been initialized (only once, on first launch)
    private var motorsInitialized = false

    /** Tag for logging */
    private val TAG = "SecretVaultApp"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SecretVaultApplication onCreate: App starting - initializing motors automatically")

        // Ensure motors initialize only once
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

    /**
     * Initializes both core motors automatically:
     1. Zero-Leak Sandbox Core (IsolationService)
     2. Auto-Configuring VPN Service (VpnAutoService)
     - Both start in the background without user interaction
     - No IP/Port settings required from the user
     */
    private fun initMotors() {
        try {
            // ---- Motor 1: Zero-Leak Sandbox Core ----
            Log.d(TAG, "Initializing Zero-Leak Sandbox Core...")
            IsolationService.enqueue(this)

            // ---- Motor 2: Auto-Configuring VPN Service ----
            Log.d(TAG, "Initializing Auto-Configuring VPN Service...")
            // Start the VPN service - it will auto-fetch proxy lists and establish tunnel
            val vpnIntent = Intent(this, VpnAutoService::class.java)
            startService(vpnIntent)

            // Also try to bind to the VPN service (requires BIND_VPN_SERVICE permission)
            try {
                bindService(vpnIntent, object : ServiceConnection {
                    override fun onServiceConnected(className: ComponentName?, iBinder: IBinder?) {
                        Log.d(TAG, "VpnAutoService connected successfully")
                    }

                    override fun onServiceDisconnected(arg0: ComponentName?) {
                        Log.w(TAG, "VpnAutoService disconnected - will reconnect")
                        // Re-attempt connection
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

    /** Returns the application context - useful for other components */
    override fun getApplicationContext(): Context {
        return super.getApplicationContext()
    }
}