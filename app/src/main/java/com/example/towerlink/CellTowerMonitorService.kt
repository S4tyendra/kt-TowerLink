package com.example.towerlink

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.telephony.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.towerlink.data.CellTowerData
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class CellTowerMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var telephonyManager: TelephonyManager

    private val lastKnownRegisteredTowers = mutableMapOf<String, CellTowerData>()

    override fun onCreate() {
        super.onCreate()
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopService()
                return START_NOT_STICKY
            }
        }

        startForegroundService()
        serviceScope.launch {
            while (isActive) {
                scanAndLogTowers()
                updateNotification()
                delay(MONITORING_INTERVAL)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cell Tower Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors cell tower changes continuously"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        val notification = buildNotification("Monitoring started...")
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun scanAndLogTowers() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            logEntry("🚨 ERROR: Location permission was revoked. Stopping service.")
            stopService()
            return
        }

        try {
            val cellInfoList = telephonyManager.allCellInfo ?: emptyList()

            val allParsedCells = cellInfoList.mapNotNull { parseCellInfoToData(it) }
            if (allParsedCells.isNotEmpty()) {
                addTowersToUniqueSet(allParsedCells.map { it.apiKeyFormat })
            }

            val currentRegisteredTowers = allParsedCells
                .filter { it.status == "Registered" }
                .associateBy { it.apiKeyFormat }

            currentRegisteredTowers.keys.forEach { key ->
                if (!lastKnownRegisteredTowers.containsKey(key)) {
                    val tower = currentRegisteredTowers[key]!!
                    logEntry("✅ SIM CONNECT: ${tower.apiKeyFormat} | ${tower.type} | ${tower.signalDbm}dBm")
                }
            }

            lastKnownRegisteredTowers.keys.forEach { key ->
                if (!currentRegisteredTowers.containsKey(key)) {
                    logEntry("🔻 SIM DISCONNECT: ${key}")
                }
            }

            lastKnownRegisteredTowers.clear()
            lastKnownRegisteredTowers.putAll(currentRegisteredTowers)

            val primarySummary = if (currentRegisteredTowers.isEmpty()) "None" else currentRegisteredTowers.keys.joinToString(", ")
            logEntry("SCAN: Found ${allParsedCells.size} cells. Registered: $primarySummary")

        } catch (e: SecurityException) {
            logEntry("🚨 SECURITY ERROR: ${e.message}. Service cannot continue. Check location permission or device settings.")
            stopService()
        } catch (e: Exception) {
            logEntry("🚨 SCAN ERROR: ${e.message}")
        }
    }

    /**
     * Parses a CellInfo object into our custom CellTowerData format.
     * Includes robust handling for 5G (CellInfoNr) NCI values which can be Long.
     */

    private fun parseCellInfoToData(cellInfo: CellInfo): CellTowerData? {
        val status = if (cellInfo.isRegistered) "Registered" else "Neighbor"

        val (type, identity, signal) = when (cellInfo) {
            is CellInfoLte -> Triple("LTE", cellInfo.cellIdentity, cellInfo.cellSignalStrength.dbm)
            is CellInfoGsm -> Triple("GSM", cellInfo.cellIdentity, cellInfo.cellSignalStrength.dbm)
            is CellInfoWcdma -> Triple("WCDMA", cellInfo.cellIdentity, cellInfo.cellSignalStrength.dbm)
            is CellInfoCdma -> Triple("CDMA", cellInfo.cellIdentity, cellInfo.cellSignalStrength.dbm)
            is CellInfoNr -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Triple("5G NR", cellInfo.cellIdentity, cellInfo.cellSignalStrength.dbm)
            } else null
            else -> null
        } ?: return null

        val (mccStr, mncStr, lacOrTacStr, cidOrNciStr) = when (identity) {
            is CellIdentityLte -> {
                if (identity.mccString == null || identity.mncString == null) return null
                listOf(identity.mccString!!, identity.mncString!!, identity.tac.toString(), identity.ci.toString())
            }
            is CellIdentityGsm -> {
                if (identity.mccString == null || identity.mncString == null) return null
                listOf(identity.mccString!!, identity.mncString!!, identity.lac.toString(), identity.cid.toString())
            }
            is CellIdentityWcdma -> {
                if (identity.mccString == null || identity.mncString == null) return null
                listOf(identity.mccString!!, identity.mncString!!, identity.lac.toString(), identity.cid.toString())
            }
            is CellIdentityCdma -> {
                // CDMA doesn't have MCC/MNC in the same way, use SID/NID instead
                // Format: SID_NID_BaseStationId_NetworkId for uniqueness
                val sid = identity.systemId
                val nid = identity.networkId
                val baseId = identity.basestationId
                if (sid == Int.MAX_VALUE || nid == Int.MAX_VALUE || baseId == Int.MAX_VALUE) return null
                listOf(sid.toString(), nid.toString(), baseId.toString(), "0") // Using 0 for consistency
            }
            is CellIdentityNr -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (identity.mccString == null || identity.mncString == null) return null
                listOf(identity.mccString!!, identity.mncString!!, identity.tac.toString(), identity.nci.toString())
            } else return null
            else -> return null
        }

        val lacAsLong = lacOrTacStr.toLongOrNull()
        val cidAsLong = cidOrNciStr.toLongOrNull()

        if (lacAsLong == null || lacAsLong == Long.MAX_VALUE || lacAsLong == -1L ||
            cidAsLong == null || cidAsLong == Long.MAX_VALUE || cidAsLong == -1L) {
            return null
        }

        return CellTowerData(
            type = type,
            status = status,
            mcc = mccStr,
            mnc = mncStr,
            lac = lacAsLong,
            cid = cidAsLong,
            signalDbm = signal
        )
    }

    private fun addTowersToUniqueSet(towerKeys: List<String>) {
        if (towerKeys.isEmpty()) return
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val existingSet = prefs.getStringSet(KEY_UNIQUE_TOWERS, emptySet()) ?: emptySet()
        val newSet = existingSet.toMutableSet()
        newSet.addAll(towerKeys)
        prefs.edit().putStringSet(KEY_UNIQUE_TOWERS, newSet).apply()
    }

    private fun stopService() {
        logEntry("⏹️ SERVICE STOPPED.")
        serviceScope.cancel()
        stopForeground(true)
        stopSelf()
    }

    private fun updateNotification() {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastLog = prefs.getString(KEY_LAST_LOG, "Waiting for first scan...")
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification(lastLog ?: "Log entry was null."))
    }

    private fun buildNotification(contentText: String): Notification {
        val stopIntent = Intent(this, CellTowerMonitorService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 1, mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📡 Cell Tower Monitor Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(mainPendingIntent)
            .addAction(0, "Stop Monitoring", stopPendingIntent)
            .build()
    }

    private fun logEntry(data: String) {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val fullEntry = "$timestamp: $data"

        val existingLogs = prefs.getString(KEY_ALL_LOGS, "") ?: ""
        // Keep logs from getting excessively large, store last ~500 entries (approx 50KB-100KB)
        val newLogs = (listOf(fullEntry) + existingLogs.split("\n")).take(15000).joinToString("\n")

        prefs.edit()
            .putString(KEY_ALL_LOGS, newLogs)
            .putString(KEY_LAST_LOG, fullEntry)
            .apply()
    }

    companion object {
        const val CHANNEL_ID = "CellTowerMonitorChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"

        const val PREF_NAME = "cell_tower_logs"
        const val KEY_ALL_LOGS = "all_logs"
        const val KEY_LAST_LOG = "last_log"
        const val KEY_UNIQUE_TOWERS = "unique_towers"

        private const val MONITORING_INTERVAL = 15_000L
    }
}