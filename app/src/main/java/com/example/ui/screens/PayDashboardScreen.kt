package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BillCategory
import com.example.data.BillTransaction
import com.example.data.Biller
import com.example.data.BillerCatalog
import com.example.data.BillerProvider
import com.example.data.PaymentRail
import com.example.data.TransactionStatus
import com.example.data.UserProfile
import com.example.ui.UiState
import com.example.ui.components.EmptyRailState
import com.example.ui.components.KpiMetric
import com.example.ui.components.KpiStrip
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.categoryIcon
import com.example.ui.theme.MonoAmountStyle
import com.example.ui.theme.MonoCodeStyle

@Composable
fun PayDashboardScreen(
    profileState: UiState<UserProfile?>,
    billersState: UiState<List<Biller>>,
    transactionsState: UiState<List<BillTransaction>>,
    isSubmitting: Boolean,
    lastReceipt: BillTransaction?,
    onDismissReceipt: () -> Unit,
    onNavigateToLegal: () -> Unit,
    onSaveBiller: (BillCategory, BillerProvider, String, String, String, Int, Boolean, () -> Unit) -> Unit,
    onUpdateBiller: (String, String, String, Int, Boolean, () -> Unit) -> Unit,
    onDeleteBiller: (String, String) -> Unit,
    onExecutePayment: (String, BillCategory, String, String, Boolean, String, PaymentRail, Boolean, (BillTransaction) -> Unit) -> Unit
) {
    var showAddBillerSheet by remember { mutableStateOf(false) }
    var addBillerCategory by remember { mutableStateOf(BillCategory.ELECTRICITY) }
    var editingBiller by remember { mutableStateOf<Biller?>(null) }

    var checkoutBiller by remember { mutableStateOf<Biller?>(null) }
    var checkoutCategory by remember { mutableStateOf<BillCategory?>(null) }

    val profile = (profileState as? UiState.Success)?.data
    val billers = (billersState as? UiState.Success)?.data.orEmpty()
    val transactions = (transactionsState as? UiState.Success)?.data.orEmpty()

    val totalSettledPaise = remember(transactions) {
        transactions
            .filter { it.statusEnum == TransactionStatus.SETTLED }
            .sumOf { it.amountPaise }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Compact Answer-First Header
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "BillRail",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Regulated BBPS & UPI Payment Desk",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(
                            text = "0 RAW PANS",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.secondary
                        )
                    }

                    // Enforceable Terms Reminder Banner (if not yet accepted)
                    if (profile != null && !profile.termsAccepted) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Gavel,
                                    contentDescription = "Legal consent pending",
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Review & Accept Terms (${BillerCatalog.CURRENT_TERMS_VERSION})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        text = "Log DPDP & BBPS auto-refund consent before production payouts.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                TextButton(
                                    onClick = onNavigateToLegal,
                                    modifier = Modifier.testTag("review_terms_banner_button")
                                ) {
                                    Text("Review")
                                }
                            }
                        }
                    }

                    KpiStrip(
                        metrics = listOf(
                            KpiMetric(
                                value = BillerCatalog.formatInrFromPaise(totalSettledPaise),
                                label = "Settled BBPS volume across ledger"
                            ),
                            KpiMetric(
                                value = "${billers.size} Billers",
                                label = "Saved masked biller profiles",
                                accent = true
                            ),
                            KpiMetric(
                                value = "4 Layers",
                                label = "Device, TLS, WAF & Zero-PAN Vault"
                            ),
                            KpiMetric(
                                value = "T+1 to T+5",
                                label = "Auto-refund SLA for failed settlements",
                                accent = true
                            )
                        )
                    )
                }
            }

            // 5 Daily Indian Bill Categories (from Blueprint)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = "Pay on Regulated Rails",
                        subtitle = "Electricity, Credit Card, Telecom & FASTag via BBPOU / UPI"
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(BillCategory.entries) { category ->
                            CategoryRailCard(
                                category = category,
                                onPayClick = {
                                    checkoutBiller = null
                                    checkoutCategory = category
                                }
                            )
                        }
                    }
                }
            }

            // Saved Billers Section
            item {
                SectionHeader(
                    title = "Saved Billers",
                    subtitle = "Masked consumer & last-4 card IDs synced per user",
                    trailing = {
                        FilledTonalButton(
                            onClick = {
                                addBillerCategory = BillCategory.ELECTRICITY
                                showAddBillerSheet = true
                            },
                            modifier = Modifier.testTag("add_biller_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Biller")
                        }
                    }
                )
            }

            when (billersState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                text = billersState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    if (billers.isEmpty()) {
                        item {
                            EmptyRailState(
                                icon = Icons.Filled.ReceiptLong,
                                title = "No Saved Billers Yet",
                                subtitle = "Add your electricity consumer ID, FASTag vehicle number, postpaid/prepaid mobile, or last-4 credit card digits for one-tap BBPS checkout.",
                                actionLabel = "Add First Biller",
                                onAction = {
                                    addBillerCategory = BillCategory.ELECTRICITY
                                    showAddBillerSheet = true
                                }
                            )
                        }
                    } else {
                        items(billers, key = { it.id }) { biller ->
                            SavedBillerCard(
                                biller = biller,
                                onPay = {
                                    checkoutBiller = biller
                                    checkoutCategory = biller.categoryEnum
                                },
                                onEdit = { editingBiller = biller },
                                onDelete = { onDeleteBiller(biller.id, biller.providerName) }
                            )
                        }
                    }
                }
            }

            // Bottom Security Architecture Footnote
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "Zero card vault protection",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "BillRail delegates checkout to NPCI UPI Intent & RBI-authorized Payment Aggregator tokens. Raw card PANs, CVVs, and UPI PINs never touch app storage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showAddBillerSheet) {
        AddBillerSheet(
            initialCategory = addBillerCategory,
            isSubmitting = isSubmitting,
            onDismiss = { showAddBillerSheet = false },
            onSave = { cat, provider, nickname, idInput, amountText, dueDay, reminder ->
                onSaveBiller(cat, provider, nickname, idInput, amountText, dueDay, reminder) {
                    showAddBillerSheet = false
                }
            }
        )
    }

    if (editingBiller != null) {
        EditBillerSheet(
            biller = editingBiller!!,
            isSubmitting = isSubmitting,
            onDismiss = { editingBiller = null },
            onUpdate = { billerId, nickname, amountText, dueDay, reminder ->
                onUpdateBiller(billerId, nickname, amountText, dueDay, reminder) {
                    editingBiller = null
                }
            }
        )
    }

    if (checkoutCategory != null) {
        BillCheckoutSheet(
            savedBiller = checkoutBiller,
            initialCategory = checkoutCategory!!,
            biometricConfirmEnabled = profile?.biometricConfirmEnabled ?: true,
            isSubmitting = isSubmitting,
            onDismiss = {
                checkoutCategory = null
                checkoutBiller = null
            },
            onSubmitPayment = { billerId, cat, providerName, idVal, alreadyMasked, amountStr, rail, pending ->
                onExecutePayment(
                    billerId,
                    cat,
                    providerName,
                    idVal,
                    alreadyMasked,
                    amountStr,
                    rail,
                    pending
                ) {
                    checkoutCategory = null
                    checkoutBiller = null
                }
            }
        )
    }

    if (lastReceipt != null) {
        ReceiptDialog(
            transaction = lastReceipt,
            onDismiss = onDismissReceipt
        )
    }
}

@Composable
private fun CategoryRailCard(
    category: BillCategory,
    onPayClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(128.dp)
            .clickable(onClick = onPayClick)
            .testTag("category_card_${category.code.lowercase()}"),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = categoryIcon(category),
                        contentDescription = category.displayName,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column {
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Pay via BBPS",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SavedBillerCard(
    biller: Biller,
    onPay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("saved_biller_card_${biller.id}"),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = categoryIcon(biller.categoryEnum),
                            contentDescription = biller.categoryEnum.displayName,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = biller.nickname,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = biller.providerName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = biller.maskedIdentifier,
                        style = MonoCodeStyle,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("edit_biller_button_${biller.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit ${biller.nickname}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Remove ${biller.nickname}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (biller.lastBillAmountPaise > 0) {
                            BillerCatalog.formatInrFromPaise(biller.lastBillAmountPaise)
                        } else {
                            "Fetch bill on pay"
                        },
                        style = MonoAmountStyle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Due day ${biller.dueDayOfMonth} · ${biller.billerCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onPay,
                    modifier = Modifier.height(42.dp)
                ) {
                    Text("Pay Now")
                }
            }
        }
    }
}
