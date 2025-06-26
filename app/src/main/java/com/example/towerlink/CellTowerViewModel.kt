package com.example.towerlink

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Represents a single cell tower's data
data class CellTower(
    val mcc: String = "N/A",
    val mnc: String = "N/A",
    val lac: String = "N/A", // Can be LAC or TAC
    val cid: String = "N/A", // Can be CID or NCI
    val type: String = "Unknown",
    val isRegistered: Boolean = false
) {
    // Helper to generate the copyable string
    val copyableFormat: String
        get() = "${mcc}_${mnc}_${lac}_$cid"
}


// Represents all data associated with one SIM card
data class SimInfo(
    val networkIdentifier: String, // e.g., "404-55"
    val towers: List<CellTower>
)

data class TowerProbeState(
    val simInfos: List<SimInfo> = emptyList(),
    val error: String? = null
)

class CellTowerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(TowerProbeState())
    val state = _state.asStateFlow()

    fun fetchCellTowerInfo() {
        val telephonyManager = getApplication<Application>().getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val context = getApplication<Application>().applicationContext

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            _state.update { it.copy(error = "Location permission not granted.") }
            return
        }

        try {
            val cellInfoList: List<CellInfo>? = telephonyManager.allCellInfo

            if (cellInfoList.isNullOrEmpty()) {
                _state.update { it.copy(error = "No cell info available.") }
                return
            }

            // 1. Parse all available towers from the raw list
            val allTowers = cellInfoList.mapNotNull { parseCellInfo(it) }

            // 2. NEW: Merge similar towers to remove duplicates[1]
            val uniqueTowers = allTowers.distinctBy { it.mcc to it.mnc to it.lac to it.cid }

            // 3. Group the unique towers by their network (MCC-MNC)
            val groupedBySim = uniqueTowers.groupBy { "${it.mcc}-${it.mnc}" }
                .map { entry ->
                    SimInfo(networkIdentifier = entry.key, towers = entry.value)
                }

            _state.update { TowerProbeState(simInfos = groupedBySim, error = null) }

        } catch (e: SecurityException) {
            _state.update { it.copy(error = "SecurityException: ${e.message}") }
        } catch (e: Exception) {
            _state.update { it.copy(error = "An unexpected error occurred: ${e.message}") }
        }
    }

    private fun parseCellInfo(cellInfo: CellInfo): CellTower? {
        return when (cellInfo) {
            is CellInfoLte -> {
                val identity = cellInfo.cellIdentity
                CellTower(mcc = identity.mccString ?: "N/A", mnc = identity.mncString ?: "N/A", lac = identity.tac.toString(), cid = identity.ci.toString(), type = "LTE", isRegistered = cellInfo.isRegistered)
            }
            is CellInfoGsm -> {
                val identity = cellInfo.cellIdentity
                CellTower(mcc = identity.mccString ?: "N/A", mnc = identity.mncString ?: "N/A", lac = identity.lac.toString(), cid = identity.cid.toString(), type = "GSM", isRegistered = cellInfo.isRegistered)
            }
            is CellInfoWcdma -> {
                val identity = cellInfo.cellIdentity
                CellTower(mcc = identity.mccString ?: "N/A", mnc = identity.mncString ?: "N/A", lac = identity.lac.toString(), cid = identity.cid.toString(), type = "WCDMA", isRegistered = cellInfo.isRegistered)
            }
            // UPDATED: Added full support for NR (5G)
            is CellInfoNr -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // CellIdentityNr can be a list, we take the first one
                val identity = cellInfo.cellIdentity as? CellIdentityNr
                identity?.let {
                    CellTower(mcc = it.mccString ?: "N/A", mnc = it.mncString ?: "N/A", lac = it.tac.toString(), cid = it.nci.toString(), type = "5G NR", isRegistered = cellInfo.isRegistered)
                }
            } else null
            else -> null
        }
    }
}
