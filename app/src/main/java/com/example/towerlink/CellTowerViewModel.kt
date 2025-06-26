package com.example.towerlink

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Data class to hold structured cell info for the UI
data class CellData(
    val type: String,
    val status: String,
    val mcc: String,
    val mnc: String,
    val locationAreaCode: Int, // LAC or TAC
    val cellId: Int, // CID or CI
    val signalDbm: Int,
    val rawDetails: String // For a quick display
)

// The new, more comprehensive UI state
data class CellTowerUiState(
    val isLoading: Boolean = false,
    val cellDataList: List<CellData> = emptyList(),
    val totalCellsFound: Int = 0,
    val phoneType: String = "Unknown",
    val error: String? = null
)

class CellTowerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CellTowerUiState())
    val uiState = _uiState.asStateFlow()

    fun fetchCellTowerInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, cellDataList = emptyList()) }

            val context = getApplication<Application>().applicationContext
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Permission Denied. Go to App Settings to grant ACCESS_FINE_LOCATION.")
                }
                return@launch
            }

            try {
                // Let's get everything!
                val cellInfoList = telephonyManager.allCellInfo
                val currentPhoneType = getPhoneTypeString(telephonyManager.phoneType)

                if (cellInfoList.isNullOrEmpty()) {
                    _uiState.update { it.copy(isLoading = false, phoneType = currentPhoneType, error = "No cell information found. Device might be offline or in a dead zone.") }
                    return@launch
                }

                // Map ALL cells, not just registered ones, to our new data class
                val allCellDetails = cellInfoList.mapNotNull { parseCellInfoToData(it) }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        cellDataList = allCellDetails,
                        totalCellsFound = cellInfoList.size,
                        phoneType = currentPhoneType
                    )
                }

            } catch (e: SecurityException) {
                _uiState.update {
                    it.copy(isLoading = false, error = "🚨 SecurityException! The system blocked the call. This can happen on some devices even with permission. Error: ${e.message}")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "An unexpected error occurred: ${e.message}")
                }
            }
        }
    }

    private fun getPhoneTypeString(phoneType: Int): String {
        return when (phoneType) {
            TelephonyManager.PHONE_TYPE_GSM -> "GSM"
            TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
            TelephonyManager.PHONE_TYPE_SIP -> "SIP"
            TelephonyManager.PHONE_TYPE_NONE -> "None"
            else -> "Unknown"
        }
    }

    private fun parseCellInfoToData(cellInfo: CellInfo): CellData? {
        val status = if (cellInfo.isRegistered) "Registered" else "Neighbor"

        return when (cellInfo) {
            is CellInfoLte -> {
                val identity = cellInfo.cellIdentity
                val signal = cellInfo.cellSignalStrength
                if (identity.mccString == null || identity.mncString == null) return null
                CellData(
                    type = "LTE (4G)", status = status,
                    mcc = identity.mccString!!, mnc = identity.mncString!!,
                    locationAreaCode = identity.tac, cellId = identity.ci,
                    signalDbm = signal.dbm,
                    rawDetails = "TAC: ${identity.tac}, CI: ${identity.ci}"
                )
            }
            is CellInfoGsm -> {
                val identity = cellInfo.cellIdentity
                val signal = cellInfo.cellSignalStrength
                if (identity.mccString == null || identity.mncString == null) return null
                CellData(
                    type = "GSM (2G)", status = status,
                    mcc = identity.mccString!!, mnc = identity.mncString!!,
                    locationAreaCode = identity.lac, cellId = identity.cid,
                    signalDbm = signal.dbm,
                    rawDetails = "LAC: ${identity.lac}, CID: ${identity.cid}"
                )
            }
            is CellInfoWcdma -> {
                val identity = cellInfo.cellIdentity
                val signal = cellInfo.cellSignalStrength
                if (identity.mccString == null || identity.mncString == null) return null
                CellData(
                    type = "WCDMA (3G)", status = status,
                    mcc = identity.mccString!!, mnc = identity.mncString!!,
                    locationAreaCode = identity.lac, cellId = identity.cid,
                    signalDbm = signal.dbm,
                    rawDetails = "LAC: ${identity.lac}, CID: ${identity.cid}"
                )
            }
            is CellInfoNr -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val identity = cellInfo.cellIdentity as android.telephony.CellIdentityNr
                val signal = cellInfo.cellSignalStrength as android.telephony.CellSignalStrengthNr
                if (identity.mccString == null || identity.mncString == null) return null
                CellData(
                    type = "NR (5G)", status = status,
                    mcc = identity.mccString!!, mnc = identity.mncString!!,
                    locationAreaCode = identity.tac, cellId = identity.nci.toInt(), // NCI is a long, cast for consistency
                    signalDbm = signal.dbm,
                    rawDetails = "TAC: ${identity.tac}, NCI: ${identity.nci}"
                )
            } else null
            else -> null
        }
    }
}