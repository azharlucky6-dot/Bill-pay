package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.BillerCatalog
import com.example.data.ComplianceGate
import com.example.data.LegalClause
import com.example.data.UserProfile
import com.example.ui.UiState
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.formatTimestampShort
import com.example.ui.theme.MonoCodeStyle

@Composable
fun LegalScreen(
    profileState: UiState<UserProfile?>,
    userEmail: String,
    isSubmitting: Boolean,
    onAcceptTerms: () -> Unit,
    onSignOut: () -> Unit
) {
    val profile = (profileState as? UiState.Success)?.data
    val termsAccepted = profile?.termsAccepted == true
    var agreeChecked by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Terms, Grievance & Compliance",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Enforceable Indian Bill-Pay Terms & Conditions (${BillerCatalog.CURRENT_TERMS_VERSION}), RBI Ombudsman escalation ladder, and Play Store launch gates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Consent Ledger Status Card
            item {
                if (termsAccepted) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = "Terms accepted",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(26.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Legal Consent Logged (${profile?.termsVersion ?: BillerCatalog.CURRENT_TERMS_VERSION})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Recorded on ${formatTimestampShort(profile?.updatedAt)} · DPDP Act 2023 & BBPS Refund Policy",
                                    style = MonoCodeStyle,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Gavel,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Record Legally Enforceable Consent",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { agreeChecked = !agreeChecked },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = agreeChecked,
                                    onCheckedChange = { agreeChecked = it },
                                    modifier = Modifier.testTag("terms_consent_checkbox")
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "I agree to the BillRail Indian Terms & Conditions (${BillerCatalog.CURRENT_TERMS_VERSION}), Zero Raw PAN policy, T+1 to T+5 auto-refund TAT, and DPDP Act 2023 data processing notice.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Button(
                                onClick = onAcceptTerms,
                                enabled = agreeChecked && !isSubmitting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("accept_terms_button")
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("Accept & Log Consent Timestamp")
                            }
                        }
                    }
                }
            }

            // Enforceable T&C Clauses
            item {
                SectionHeader(
                    title = "Terms & Conditions Clauses",
                    subtitle = "Tap any clause to inspect full legal disclosure"
                )
            }

            items(BillerCatalog.legalClauses, key = { it.number }) { clause ->
                ExpandableLegalClauseCard(clause)
            }

            // Statutory Grievance Officer Escalation Matrix
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(
                        title = "Grievance Officer & Escalation Matrix",
                        subtitle = "Statutory Indian payment redressal tiers (NPCI / RBI)"
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            EscalationTierRow(
                                tier = "LEVEL 1 · T+1 to T+5 DAYS",
                                title = "BBPOU Support Desk (In-App Ledger Dispute)",
                                detail = "Log directly via Ledger -> Raise BBPS Dispute with your BBPS Reference ID."
                            )
                            EscalationTierRow(
                                tier = "LEVEL 2 · WITHIN 15 DAYS",
                                title = "Designated Nodal Grievance Officer (India)",
                                detail = "Statutory escalation if Level 1 remains unresolved or refund exceeds Harmonised TAT."
                            )
                            EscalationTierRow(
                                tier = "LEVEL 3 · AFTER 30 DAYS",
                                title = "RBI Integrated Ombudsman Scheme (RB-IOS)",
                                detail = "Statutory right to approach the Reserve Bank of India CMS portal if unresolved."
                            )
                        }
                    }
                }
            }

            // Play Store & Launch Compliance Checklist
            item {
                SectionHeader(
                    title = "Play Store & Hardening Checklist",
                    subtitle = "Pre-launch compliance gates from security blueprint"
                )
            }

            items(BillerCatalog.complianceGates, key = { it.id }) { gate ->
                ComplianceGateCard(gate)
            }

            // Account & Session Footer
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = "Authenticated Session",
                        subtitle = userEmail.ifBlank { "Google Sign-In active" }
                    )
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out & Clear Credential Session")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandableLegalClauseCard(clause: LegalClause) {
    var expanded by remember { mutableStateOf(clause.number == "01") }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = clause.number,
                    style = MonoCodeStyle,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = clause.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse clause" else "Expand clause",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = clause.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = clause.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun EscalationTierRow(tier: String, title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = tier,
            style = MonoCodeStyle,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ComplianceGateCard(gate: ComplianceGate) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (gate.enforcedInApp) Icons.Filled.CheckCircle else Icons.Filled.Schedule,
                contentDescription = null,
                tint = if (gate.enforcedInApp) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = gate.phase,
                        style = MonoCodeStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StatusPill(
                        text = if (gate.enforcedInApp) "ENFORCED" else "PRE-LAUNCH",
                        containerColor = if (gate.enforcedInApp) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        contentColor = if (gate.enforcedInApp) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
                Text(
                    text = gate.title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = gate.requirement,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
