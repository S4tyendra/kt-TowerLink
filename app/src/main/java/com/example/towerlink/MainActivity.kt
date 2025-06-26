package com.example.towerlink

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.towerlink.ui.theme.TowerLinkTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class)
class MainActivity : ComponentActivity() {

    private val viewModel: CellTowerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TowerLinkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CellTowerProbeScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CellTowerProbeScreen(viewModel: CellTowerViewModel) {
    val locationPermissionState = rememberPermissionState(
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(locationPermissionState.status) {
        if (locationPermissionState.status.isGranted) {
            viewModel.fetchCellTowerInfo()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cell Tower Probe") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            if (locationPermissionState.status.isGranted) {
                Button(
                    onClick = { viewModel.fetchCellTowerInfo() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Refresh Cell Info")
                }
            } else {
                PermissionRequestUI(locationPermissionState)
            }

            state.error?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Error: $it",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(state.simInfos) { simInfo ->
                    SimInfoCard(simInfo = simInfo)
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRequestUI(locationPermissionState: PermissionState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        val textToShow = if (locationPermissionState.status.shouldShowRationale) {
            "Location permission is needed to read cell tower info."
        } else {
            "This app needs location permission to function."
        }
        Text(textToShow)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { locationPermissionState.launchPermissionRequest() }) {
            Text("Grant Permission")
        }
    }
}

@Composable
fun SimInfoCard(simInfo: SimInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = "Network: ${simInfo.networkIdentifier}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Divider()
            simInfo.towers.forEach { tower ->
                TowerDataDisplay(tower = tower)
            }
        }
    }
}

@Composable
fun TowerDataDisplay(tower: CellTower) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val backgroundColor = if (tower.isRegistered) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // NEW: Add clickable modifier for tap-to-copy
            .clickable {
                clipboardManager.setText(AnnotatedString(tower.copyableFormat))
                Toast
                    .makeText(context, "Copied: ${tower.copyableFormat}", Toast.LENGTH_SHORT)
                    .show()
            }
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                if (tower.isRegistered) {
                    Text(
                        "Connected Tower",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Text(
                    text = tower.copyableFormat,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Text(
                text = tower.type,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
