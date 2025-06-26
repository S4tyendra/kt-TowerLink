package com.example.towerlink

import android.app.Application
import android.content.Context
import android.os.Build
import android.telephony.*
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CellTowerData(
    val mcc: Int,
    val mnc: Int,
    val lac: Int, // LAC for GSM/CDMA, TAC for LTE
    val cid: Int, // Cell ID
    val networkType: String,
    val simSlot: Int,
    val signalStrength: Int? = null
)

data class CellTowerUiState(
    val isLoading: Boolean = false,
    val cellTowerData: List<CellTowerData> = emptyList(),
    val error: String? = null,
    val debugInfo: String = ""
)

class CellTowerViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private val _uiState = MutableStateFlow(CellTowerUiState())
    val uiState: StateFlow<CellTowerUiState> = _uiState.asStateFlow()

    @RequiresApi(Build.VERSION_CODES.R)
    fun probeCellTowers() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, debugInfo = "")

        try {
            val cellInfoList = telephonyManager.allCellInfo
            val debugInfo = StringBuilder()
            val cellTowerDataList = mutableListOf<CellTowerData>()

            debugInfo.appendLine("📊 Total cells found: ${cellInfoList?.size ?: 0}")
            debugInfo.appendLine("📱 Active SIM slots: ${telephonyManager.activeModemCount}")
            debugInfo.appendLine("📞 Phone type: ${getPhoneTypeName(telephonyManager.phoneType)}")

            if (cellInfoList.isNullOrEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "No cell towers found. Make sure location permission is granted and you're not in airplane mode.",
                    debugInfo = debugInfo.toString()
                )
                return
            }

            cellInfoList.forEachIndexed { index, cellInfo ->
                debugInfo.appendLine("\n--- Cell $index ---")
                debugInfo.appendLine("Registered: ${cellInfo.isRegistered}")
                debugInfo.appendLine("Type: ${cellInfo.javaClass.simpleName}")

                val towerData = when (cellInfo) {
                    is CellInfoGsm -> {
                        val identity = cellInfo.cellIdentity
                        val signalStrength = cellInfo.cellSignalStrength?.dbm

                        debugInfo.appendLine("GSM - MCC: ${identity.mcc}, MNC: ${identity.mnc}")
                        debugInfo.appendLine("LAC: ${identity.lac}, CID: ${identity.cid}")
                        debugInfo.appendLine("Signal: ${signalStrength}dBm")

                        CellTowerData(
                            mcc = identity.mcc,
                            mnc = identity.mnc,
                            lac = identity.lac,
                            cid = identity.cid,
                            networkType = "GSM",
                            simSlot = getSimSlotFromCellInfo(cellInfo),
                            signalStrength = signalStrength
                        )
                    }

                    is CellInfoLte -> {
                        val identity = cellInfo.cellIdentity
                        val signalStrength = cellInfo.cellSignalStrength?.dbm

                        debugInfo.appendLine("LTE - MCC: ${identity.mcc}, MNC: ${identity.mnc}")
                        debugInfo.appendLine("TAC: ${identity.tac}, CID: ${identity.ci}")
                        debugInfo.appendLine("Signal: ${signalStrength}dBm")

                        CellTowerData(
                            mcc = identity.mcc,
                            mnc = identity.mnc,
                            lac = identity.tac, // TAC for LTE
                            cid = identity.ci,
                            networkType = "LTE",
                            simSlot = getSimSlotFromCellInfo(cellInfo),
                            signalStrength = signalStrength
                        )
                    }

                    is CellInfoWcdma -> {
                        val identity = cellInfo.cellIdentity
                        val signalStrength = cellInfo.cellSignalStrength?.dbm

                        debugInfo.appendLine("WCDMA - MCC: ${identity.mcc}, MNC: ${identity.mnc}")
                        debugInfo.appendLine("LAC: ${identity.lac}, CID: ${identity.cid}")
                        debugInfo.appendLine("Signal: ${signalStrength}dBm")

                        CellTowerData(
                            mcc = identity.mcc,
                            mnc = identity.mnc,
                            lac = identity.lac,
                            cid = identity.cid,
                            networkType = "WCDMA",
                            simSlot = getSimSlotFromCellInfo(cellInfo),
                            signalStrength = signalStrength
                        )
                    }

                    is CellInfoCdma -> {
                        val identity = cellInfo.cellIdentity
                        val signalStrength = cellInfo.cellSignalStrength?.dbm

                        debugInfo.appendLine("CDMA - Network ID: ${identity.networkId}, System ID: ${identity.systemId}")
                        debugInfo.appendLine("Base Station ID: ${identity.basestationId}")
                        debugInfo.appendLine("Signal: ${signalStrength}dBm")

                        // CDMA doesn't have MCC/MNC/LAC in the traditional sense
                        // We'll use Network ID as MNC and System ID as LAC
                        CellTowerData(
                            mcc = 310, // Default US MCC for CDMA
                            mnc = identity.networkId,
                            lac = identity.systemId,
                            cid = identity.basestationId,
                            networkType = "CDMA",
                            simSlot = getSimSlotFromCellInfo(cellInfo),
                            signalStrength = signalStrength
                        )
                    }

                    is CellInfoNr -> {
                        val identity = cellInfo.cellIdentity as CellIdentityNr
                        val signalStrength = cellInfo.cellSignalStrength?.dbm

                        debugInfo.appendLine("5G NR - MCC: ${identity.mccString}, MNC: ${identity.mncString}")
                        debugInfo.appendLine("TAC: ${identity.tac}, NCI: ${identity.nci}")
                        debugInfo.appendLine("Signal: ${signalStrength}dBm")

                        CellTowerData(
                            mcc = identity.mccString?.toIntOrNull() ?: 0,
                            mnc = identity.mncString?.toIntOrNull() ?: 0,
                            lac = identity.tac,
                            cid = (identity.nci and 0xFFFFFFFF).toInt(), // Convert long to int
                            networkType = "5G NR",
                            simSlot = getSimSlotFromCellInfo(cellInfo),
                            signalStrength = signalStrength
                        )
                    }

                    else -> {
                        debugInfo.appendLine("Unknown cell type: ${cellInfo.javaClass.simpleName}")
                        null
                    }
                }

                // Only add valid tower data (exclude unknown values)
                towerData?.let {
                    if (it.mcc != Int.MAX_VALUE && it.mnc != Int.MAX_VALUE &&
                        it.lac != Int.MAX_VALUE && it.cid != Int.MAX_VALUE &&
                        it.mcc > 0 && it.mnc >= 0 && it.lac > 0 && it.cid > 0) {
                        cellTowerDataList.add(it)
                    } else {
                        debugInfo.appendLine("Skipped invalid data: MCC=${it.mcc}, MNC=${it.mnc}, LAC=${it.lac}, CID=${it.cid}")
                    }
                }
            }

            // Sort by registered cells first, then by signal strength
            val sortedData = cellTowerDataList.sortedWith(
                compareByDescending<CellTowerData> { tower ->
                    // Check if this tower corresponds to a registered cell
                    cellInfoList.any { cellInfo ->
                        cellInfo.isRegistered && matchesTowerWithCellInfo(tower, cellInfo)
                    }
                }.thenByDescending { it.signalStrength ?: Int.MIN_VALUE }
            )

            debugInfo.appendLine("\n🎯 Valid towers found: ${sortedData.size}")
            if (sortedData.isNotEmpty()) {
                debugInfo.appendLine("🏆 Primary tower: ${sortedData.first().mcc}_${sortedData.first().mnc}_${sortedData.first().lac}_${sortedData.first().cid}")
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                cellTowerData = sortedData,
                debugInfo = debugInfo.toString(),
                error = if (sortedData.isEmpty()) "No valid cell tower data found" else null
            )

        } catch (e: SecurityException) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Security error: ${e.message}. Make sure location permission is granted.",
                debugInfo = "SecurityException: ${e.localizedMessage}"
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Unexpected error: ${e.message}",
                debugInfo = "Exception: ${e.localizedMessage}\nStack: ${e.stackTrace.take(5).joinToString("\n")}"
            )
        }
    }

    private fun getSimSlotFromCellInfo(cellInfo: CellInfo): Int {
        // Try to get subscription ID if available (API 29+)
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Use reflection to get subscriptionId since it's not always available
                val subscriptionIdField = cellInfo.javaClass.getDeclaredField("mSubscriptionId")
                subscriptionIdField.isAccessible = true
                val subscriptionId = subscriptionIdField.getInt(cellInfo)

                if (subscriptionId != Int.MAX_VALUE && subscriptionId != -1) {
                    // Convert subscription ID to SIM slot (0-based)
                    val defaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
                    if (subscriptionId == defaultDataSubId) 0 else 1
                } else 0
            } else {
                0 // Default to SIM 1 for older APIs
            }
        } catch (e: Exception) {
            // If reflection fails or field doesn't exist, try alternative approach
            try {
                // Try to determine from registered state and signal strength
                if (cellInfo.isRegistered) 0 else 1
            } catch (e2: Exception) {
                0 // Ultimate fallback to SIM 1
            }
        }
    }

    private fun matchesTowerWithCellInfo(tower: CellTowerData, cellInfo: CellInfo): Boolean {
        return when (cellInfo) {
            is CellInfoGsm -> {
                val identity = cellInfo.cellIdentity
                tower.mcc == identity.mcc && tower.mnc == identity.mnc &&
                        tower.lac == identity.lac && tower.cid == identity.cid
            }
            is CellInfoLte -> {
                val identity = cellInfo.cellIdentity
                tower.mcc == identity.mcc && tower.mnc == identity.mnc &&
                        tower.lac == identity.tac && tower.cid == identity.ci
            }
            is CellInfoWcdma -> {
                val identity = cellInfo.cellIdentity
                tower.mcc == identity.mcc && tower.mnc == identity.mnc &&
                        tower.lac == identity.lac && tower.cid == identity.cid
            }
            is CellInfoNr -> {
                val identity = cellInfo.cellIdentity as CellIdentityNr
                tower.mcc == (identity.mccString?.toIntOrNull() ?: 0) &&
                        tower.mnc == (identity.mncString?.toIntOrNull() ?: 0) &&
                        tower.lac == identity.tac &&
                        tower.cid == (identity.nci and 0xFFFFFFFF).toInt()
            }
            else -> false
        }
    }

    private fun getPhoneTypeName(phoneType: Int): String {
        return when (phoneType) {
            TelephonyManager.PHONE_TYPE_GSM -> "GSM"
            TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
            TelephonyManager.PHONE_TYPE_SIP -> "SIP"
            TelephonyManager.PHONE_TYPE_NONE -> "None"
            else -> "Unknown ($phoneType)"
        }
    }
}