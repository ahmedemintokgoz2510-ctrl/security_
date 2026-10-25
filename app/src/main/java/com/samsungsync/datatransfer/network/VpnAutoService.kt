package com.samsungsync.datatransfer.network

import android.net.VpnService
import android.content.Intent
import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import java.net.URL
import java.net.HttpURLConnection

class VpnAutoService : VpnService() {

    private val TAG = "VpnAutoService"
    private var targetAddress: String? = null
    private var targetPort: Int = 0
    private var vpnEstablished = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VpnAutoService onCreate: Auto-VPN starting without user configuration")
        FetchProxyTask().execute()
    }

    private inner class FetchProxyTask : AsyncTask<Void, Void, String?>() {

        override fun doInBackground(vararg params: Void?): String? {
            try {
                val proxySources = listOf(
                    "https://api.proxyscrape.com/v2/?request=proxies&protocol=socks5&timeout=8000",
                    "https://toringest.torproject.org/entrynodes"
                )

                for (source in proxySources) {
                    val fetched = fetchUrl(source)
                    if (!fetched.isNullOrEmpty()) {
                        val validProxies = parseProxies(fetched)
                        if (validProxies.isNotEmpty()) {
                            targetAddress = validProxies[0].address
                            targetPort = validProxies[0].port
                            Log.d(TAG, "Selected exit node: $targetAddress:$targetPort")
                            return "$targetAddress:$targetPort"
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Proxy fetch failed", e)
            }
            return null
        }

        private fun fetchUrl(urlString: String): String? {
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.connect()
                return connection.inputStream.bufferedReader().readText()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch $urlString", e)
                return null
            }
        }

        private fun parseProxies(proxyText: String): List<ProxyInfo> {
            val results = mutableListOf<ProxyInfo>()
            val lines = proxyText.split(Regex("\\r?\\n"))
            for (line in lines) {
                if (line.trim().isEmpty()) continue
                val parts = line.split(":")
                if (parts.size >= 2) {
                    try {
                        val address = parts[0].trim()
                        val portStr = parts[1].trim()
                        val port = portStr.toInt()
                        if (port in 1..65535 && !address.startsWith("//")) {
                            results.add(ProxyInfo(address, port))
                        }
                    } catch (e: NumberFormatException) {
                        // Skip malformed entries
                    }
                }
            }
            return results
        }

        data class ProxyInfo(val address: String, val port: Int)
    }

    private fun establishVpnTunnel() {
        targetAddress?.let { exitAddress ->
            try {
                // FIXED: VpnService.Builder takes NO arguments
                val builder = VpnService.Builder()
                builder.addAddress(exitAddress, 24)
                builder.addRoute("0.0.0.0", 0)

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    builder.addDnsServer(exitAddress)
                }

                val pfd: ParcelFileDescriptor? = builder.establish()
                if (pfd != null) {
                    Log.d(TAG, "VPN tunnel established successfully")
                    vpnEstablished = true
                } else {
                    handleEstablishmentError()
                }
            } catch (e: Exception) {
                Log.e(TAG, "VPN establishment exception", e)
                handleEstablishmentError()
            }
        } ?: run {
            Log.w(TAG, "Cannot establish VPN: no exit node address fetched")
            handleEstablishmentError()
        }
    }

    private fun handleEstablishmentError() {
        Log.e(TAG, "VPN establishment failed/error")
        Handler(Looper.getMainLooper()).postDelayed({
            FetchProxyTask().execute()
        }, 5000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        establishVpnTunnel()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "VpnAutoService destroyed - VPN tunnel closing")
    }
}
