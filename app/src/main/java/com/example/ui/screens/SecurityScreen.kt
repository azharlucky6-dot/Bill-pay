package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Biller
import com.example.data.UserProfile
import com.example.security.DeviceSecurityInspector
import com.example.security.SecurityLayerCheck
import com.example.ui.UiState
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.CobaltDark
import com.example.ui.theme.MonoCodeStyle

@Composable
fun SecurityScreen(
    profileState: UiState<UserProfile?>,
    billersState: UiState<List<Biller>>,
    onUpdateSecurityPreferences: (screenShield: Boolean, biometricConfirm: Boolean) -> Unit
) {
    val context = LocalContext.current
    val profile = (profileState as? UiState.Success)?.data
    val billers = (billersState as? UiState.Success)?.data.orEmpty()

    val screenShieldEnabled = profile?.screenShieldEnabled ?: false
    val biometricConfirmEnabled = profile?.biometricConfirmEnabled ?: true

    var refreshNonce by remember { mutableIntStateOf(0) }
    val rawPanRegex = remember { Regex("^[0-9\\s\\-]{13,23}$") }
    val rawPanViolations = remember(billers) {
        billers.count { rawPanRegex.matches(it.maskedIdentifier) }
    }

    val snapshot by produceState<com.example.security.DeviceSecuritySnapshot?>(
        initialValue = null,
        key1 = context,
        key2 = screenShieldEnabled,
        key3 = rawPanViolations to refreshNonce
    ) {
        value = withContext(Dispatchers.IO) {
            DeviceSecurityInspector.inspect(
                context = context,
                screenShieldEnabled = screenShieldEnabled,
                zeroRawPanCount = rawPanViolations
            )
        }
    }

    val passedCount = snapshot?.passedCount ?: 0
    val totalCount = snapshot?.totalCount ?: 5
    val transportLabel = snapshot?.networkTransportLabel ?: "Checking TLS..."
    val checks = snapshot?.checks.orEmpty()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Security Hero Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                        .clip(MaterialTheme.shapes.large)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_security_rails_hero_1795167503029),
                        contentDescription = "Four-layer security defense banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        CobaltDark.copy(alpha = 0.94f),
                                        CobaltDark.copy(alpha = 0.60f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill(
                                text = "$passedCount/$totalCount CONTROLS ACTIVE",
                                containerColor = Color.White.copy(alpha = 0.16f),
                                contentColor = Color.White
                            )
                            IconButton(
                                onClick = { refreshNonce++ },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("refresh_security_telemetry_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Re-scan device security telemetry",
                                    tint = Color.White
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "4-Layer OWASP Security Posture",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White
                            )
                            Text(
                                text = "Live Android device, transport, rail isolation & zero-PAN audit.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.88f)
                            )
                        }
                    }
                }
            }

            // Live Interactive Controls
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = "Active Client Controls",
                        subtitle = "Synced to your isolated Firestore security profile"
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "FLAG_SECURE Screen Shield",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Blocks Android screenshots & task-switcher previews on live window.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Switch(
                                    checked = screenShieldEnabled,
                                    onCheckedChange = { enabled ->
                                        onUpdateSecurityPreferences(enabled, biometricConfirmEnabled)
                                    },
                                    modifier = Modifier.testTag("toggle_screen_shield")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Explicit Checkout Verification Guard",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Requires explicit biller ID & amount confirmation before payout.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Switch(
                                    checked = biometricConfirmEnabled,
                                    onCheckedChange = { enabled ->
                                        onUpdateSecurityPreferences(screenShieldEnabled, enabled)
                                    },
                                    modifier = Modifier.testTag("toggle_checkout_guard")
                                )
                            }
                        }
                    }
                }
            }

            // Live Telemetry Audit Results
            item {
                SectionHeader(
                    title = "Live Runtime Telemetry",
                    subtitle = "Inspected directly from Android OS APIs ($transportLabel)"
                )
            }

            items(checks) { check ->
                SecurityCheckCard(check)
            }

            // 4-Stage Regulated Rail Architecture Schematic
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(
                        title = "4-Stage Regulated Rail Architecture",
                        subtitle = "Separates consumer UI from regulated money movement"
                    )
                    ArchitectureStageCard(
                        stage = "STAGE 01",
                        title = "Hardened Android Client",
                        detail = "Jetpack Credential Manager Google Sign-In, FLAG_SECURE, allowBackup=false, zero raw PAN input."
                    )
                    StageConnector()
                    ArchitectureStageCard(
                        stage = "STAGE 02",
                        title = "TLS 1.3 Transport & Cloud WAF",
                        detail = "Encrypted HTTPS transport with certificate pinning & rate-limited API gateway fronting."
                    )
                    StageConnector()
                    ArchitectureStageCard(
                        stage = "STAGE 03",
                        title = "Licensed BBPOU Agent API",
                        detail = "Signed Bharat Connect (BBPS) bill fetch & B-Assured reference settlement across 5 categories."
                    )
                    StageConnector()
                    ArchitectureStageCard(
                        stage = "STAGE 04",
                        title = "UPI Intent & RBI Token Vault",
                        detail = "Raw PANs, CVVs, and UPI PINs remain strictly inside NPCI UPI apps and PCI-DSS PA SDKs."
                    )
                }
            }
        }
    }
}

@Composable
private fun SecurityCheckCard(check: SecurityLayerCheck) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = check.layerName,
                    style = MonoCodeStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusPill(
                    text = check.statusBadge,
                    containerColor = if (check.passed) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                    contentColor = if (check.passed) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (check.passed) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (check.passed) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = check.checkTitle,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = check.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ArchitectureStageCard(stage: String, title: String, detail: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stage,
                style = MonoCodeStyle,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StageConnector() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.ArrowDownward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}
