package com.example.towerlink

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.towerlink.CellTowerMonitorService.Companion.KEY_ALL_LOGS
import com.example.towerlink.CellTowerMonitorService.Companion.KEY_UNIQUE_TOWERS
import com.example.towerlink.CellTowerMonitorService.Companion.PREF_NAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val logs: String = "No logs yet. Start the monitoring service!",
)

class CellTowerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    private val sharedPrefs: SharedPreferences = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_ALL_LOGS) {
            loadLogs()
        }
    }

    init {
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        loadLogs()
    }

    fun loadLogs() {
        viewModelScope.launch {
            val logs = sharedPrefs.getString(KEY_ALL_LOGS, "No logs found. Start monitoring.") ?: "Logs key returned null."
            _uiState.update { it.copy(logs = logs) }
        }
    }

    /**
     * Retrieves all unique tower API keys stored by the service, formatted for clipboard.
     */
    suspend fun getUniqueTowersForClipboard(): String {
        return withContext(Dispatchers.IO) {
            val towerSet = sharedPrefs.getStringSet(KEY_UNIQUE_TOWERS, null)
            if (towerSet.isNullOrEmpty()) {
                "No unique towers have been logged yet."
            } else {
                towerSet.sorted().joinToString("\n")
            }
        }
    }

    /**
     * Clears all stored logs and unique tower data.
     */
    fun clearLogs() {
        viewModelScope.launch {
            sharedPrefs.edit()
                .remove(KEY_ALL_LOGS)
                .remove(KEY_UNIQUE_TOWERS)
                .remove(CellTowerMonitorService.KEY_LAST_LOG)
                .apply()
            _uiState.update { it.copy(logs = "Logs cleared.") }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}