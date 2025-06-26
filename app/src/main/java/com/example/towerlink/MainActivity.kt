package com.example.towerlink

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.Manifest
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.towerlink.ui.theme.TowerLinkTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch
import com.slaviboy.iconscompose.Icon
import com.slaviboy.iconscompose.R

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TowerLinkTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LogScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalAnimationApi::class)
@Composable
fun LogScreen(modifier: Modifier = Modifier, viewModel: CellTowerViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Animation states
    var isServiceRunning by remember { mutableStateOf(false) }
    val pulseAnimation by animateFloatAsState(
        targetValue = if (isServiceRunning) 1.02f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        )
    )

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val permissionState = rememberMultiplePermissionsState(permissions = permissionsToRequest)

    LaunchedEffect(Unit) {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with animation
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = tween(800, easing = EaseOutBounce)
                )
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(pulseAnimation),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "🛰️ TowerLink",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Cell Tower Monitor",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Permission status with smooth transitions
            AnimatedVisibility(
                visible = true,
                enter = slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(600, delayMillis = 200)
                )
            ) {
                StatusCard(permissionState.allPermissionsGranted)
            }

            Spacer(Modifier.height(20.dp))

            // Control buttons or permission request
            AnimatedContent(
                targetState = permissionState.allPermissionsGranted,
                transitionSpec = {
                    fadeIn(animationSpec = tween(600)) with fadeOut(animationSpec = tween(600))
                }
            ) { hasPermissions ->
                if (hasPermissions) {
                    EnhancedControlButtons(
                        viewModel = viewModel,
                        onServiceStateChange = { isServiceRunning = it },
                        onClearLogs = { viewModel.clearLogs() },
                        onCopyAllLogs = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Cell Tower Logs", uiState.logs)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "All logs copied! 📋", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    Button(
                        onClick = { permissionState.launchMultiplePermissionRequest() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {

                        Icon(
                            modifier = Modifier
                                .width(15.dp)
                                .height(15.dp),
                            type = R.drawable.fi_br_lock,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )

                        Spacer(Modifier.width(8.dp))
                        Text("🔐 Grant Permissions")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Enhanced log display
            EnhancedLogDisplay(
                logs = uiState.logs,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun StatusCard(hasPermissions: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasPermissions)
                Color(0xFF4CAF50).copy(alpha = 0.1f)
            else
                Color(0xFFF44336).copy(alpha = 0.1f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            if (hasPermissions) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier
                    .width(15.dp)
                    .height(15.dp),
                type = if (hasPermissions) R.drawable.fi_br_check else R.drawable.fi_br_exclamation,
                color = if (hasPermissions) Color(0xFF4CAF50) else Color(0xFFF44336)
            )

            Spacer(Modifier.width(12.dp))
            Text(
                text = if (hasPermissions) "✅ All Systems Ready" else "❌ Permissions Required",
                fontWeight = FontWeight.Medium,
                color = if (hasPermissions) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
        }
    }
}

@Composable
fun EnhancedControlButtons(
    viewModel: CellTowerViewModel,
    onServiceStateChange: (Boolean) -> Unit,
    onClearLogs: () -> Unit,
    onCopyAllLogs: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Primary controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    val intent = Intent(context, CellTowerMonitorService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                    onServiceStateChange(true)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {

                Icon(
                    modifier = Modifier
                        .width(15.dp)
                        .height(15.dp),
                    type = R.drawable.fi_br_play,
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
                )

                Spacer(Modifier.width(4.dp))
                Text("START", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    val intent = Intent(context, CellTowerMonitorService::class.java).apply {
                        action = CellTowerMonitorService.ACTION_STOP_SERVICE
                    }
                    context.startService(intent)
                    onServiceStateChange(false)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF5722)
                )
            ) {

                Icon(
                    modifier = Modifier
                        .width(15.dp)
                        .height(15.dp),
                    type = R.drawable.fi_br_stop,
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
                )

                Spacer(Modifier.width(4.dp))
                Text("STOP", fontWeight = FontWeight.Bold)
            }
        }

        // Secondary controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onClearLogs,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {

                Icon(
                    modifier = Modifier
                        .width(15.dp)
                        .height(15.dp),
                    type = R.drawable.fi_br_broom,
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
                )

                Spacer(Modifier.width(4.dp))
                Text("CLEAR")
            }

            Button(
                onClick = onCopyAllLogs,
                modifier = Modifier.weight(1f)
            ) {

                Icon(
                    modifier = Modifier
                        .width(15.dp)
                        .height(15.dp),
                    type = R.drawable.fi_br_duplicate,
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
                )

                Spacer(Modifier.width(4.dp))
                Text("COPY ALL")
            }
        }

        // Unique keys button
        Button(
            onClick = {
                coroutineScope.launch {
                    val uniqueTowers = viewModel.getUniqueTowersForClipboard()
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Unique Cell Tower Keys", uniqueTowers)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Copied ${uniqueTowers.lines().size} unique tower keys! ✨", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            )
        ) {

            Icon(
                modifier = Modifier
                    .width(15.dp)
                    .height(15.dp),
                type = R.drawable.fi_br_key,
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
            )

            Spacer(Modifier.width(8.dp))
            Text("COPY UNIQUE KEYS", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EnhancedLogDisplay(
    logs: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "📊 Live Monitoring Log",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Log count badge
            val logCount = logs.lines().filter { it.isNotBlank() }.size
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = "$logCount entries",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxSize(),
            elevation = CardDefaults.cardElevation(4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            val scrollState = rememberScrollState()
            LaunchedEffect(logs) {
                if (scrollState.maxValue > 0) {
                    scrollState.animateScrollTo(0)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                if (logs.isBlank() || logs == "No logs found. Start monitoring.") {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            modifier = Modifier
                                .width(15.dp)
                                .height(15.dp),
                            type = R.drawable.fi_br_signal_alt,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )

                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No monitoring data yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            "Start the service to begin logging",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    Text(
                        text = logs,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}