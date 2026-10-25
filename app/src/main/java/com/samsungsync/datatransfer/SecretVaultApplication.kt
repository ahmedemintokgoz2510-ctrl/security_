package com.samsungsync.datatransfer

import android.app.Application

/**
 * Application initialization is intentionally passive.
 * Network/VPN and app-launch services must only run after an explicit,
 * user-visible action; they are not started automatically at app launch.
 */
class SecretVaultApplication : Application()
