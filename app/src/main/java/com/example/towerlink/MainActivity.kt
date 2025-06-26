package com.example.towerlink

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.towerlink.ui.theme.TowerLinkTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TowerLinkTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CellTowerProbeScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CellTowerProbeScreen(modifier: Modifier = Modifier, viewModel: CellTowerViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val locationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Cell Tower Probe 🛰️", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        if (locationPermissionState.status.isGranted) {
            Button(onClick = { viewModel.fetchCellTowerInfo() }, modifier = Modifier.fillMaxWidth()) {
                Text("REFRESH CELL INFO")
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val textToShow = if (locationPermissionState.status.shouldShowRationale) {
                    "This app needs location to scan cell towers. It's the whole point! Please grant the permission. 😉"
                } else {
                    "Location permission is required. Click below to grant it."
                }
                Text(textToShow, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { locationPermissionState.launchPermissionRequest() }) {
                    Text("GRANT PERMISSION")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- Display Area ---
        when {
            uiState.isLoading -> CircularProgressIndicator()
            uiState.error != null -> ErrorCard(error = uiState.error!!)
            else -> ResultsDisplay(uiState)
        }
    }
}

@Composable
fun ResultsDisplay(uiState: CellTowerUiState) {
    if (uiState.totalCellsFound == 0 && !uiState.isLoading) {
        Text("Click 'Refresh' to start scanning...", style = MaterialTheme.typography.bodyLarge)
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            DebugSummaryCard(
                total = uiState.totalCellsFound,
                registered = uiState.cellDataList.count { it.status == "Registered" },
                phoneType = uiState.phoneType
            )
        }
        items(uiState.cellDataList) { cellData ->
            CellDataCard(data = cellData)
        }
    }
}

@Composable
fun DebugSummaryCard(total: Int, registered: Int, phoneType: String) {
    Card(elevation = CardDefaults.cardElevation(4.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Total Found", style = MaterialTheme.typography.labelMedium)
                Text("$total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Registered", style = MaterialTheme.typography.labelMedium)
                Text("$registered", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Phone Type", style = MaterialTheme.typography.labelMedium)
                Text(phoneType, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CellDataCard(data: CellData) {
    Card(
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (data.status == "Registered") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("📡 ${data.type}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    text = data.status.uppercase(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (data.status == "Registered") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                InfoColumn(label = "MCC", value = data.mcc)
                InfoColumn(label = "MNC", value = data.mnc)
                InfoColumn(label = "Signal", value = "${data.signalDbm} dBm")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "ID: ${data.rawDetails}",
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun RowScope.InfoColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, fontWeight = FontWeight.Medium, fontSize = 16.sp)
    }
}

@Composable
fun ErrorCard(error: String) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("❌ Error", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onError)
            Spacer(modifier = Modifier.height(8.dp))
            Text(error, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onError)

            // If it's a security exception, offer a link to settings
            if (error.contains("SecurityException", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.fromParts("package", context.packageName, null)
                    context.startActivity(intent)
                }) {
                    Text("OPEN APP SETTINGS")
                }
            }
        }
    }
}