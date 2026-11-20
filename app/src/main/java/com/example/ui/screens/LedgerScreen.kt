package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BillTransaction
import com.example.data.BillerCatalog
import com.example.data.GrievanceReason
import com.example.data.GrievanceStatus
import com.example.data.GrievanceTicket
import com.example.data.TransactionStatus
import com.example.ui.UiState
import com.example.ui.components.EmptyRailState
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.categoryIcon
import com.example.ui.components.formatTimestampShort
import com.example.ui.theme.MonoAmountStyle
import com.example.ui.theme.MonoCodeStyle

private enum class LedgerFilter(val label: String) {
    ALL("All"),
    SETTLED("Settled"),
    PENDING_OR_REFUND("Pending / Refund")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LedgerScreen(
    transactionsState: UiState<List<BillTransaction>>,
    grievancesState: UiState<List<GrievanceTicket>>,
    isSubmitting: Boolean,
    onUpdateTransactionStatus: (String, TransactionStatus) -> Unit,
    onRaiseGrievance: (BillTransaction, GrievanceReason, String, () -> Unit) -> Unit,
    onUpdateGrievanceStatus: (String, GrievanceStatus, String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf(LedgerFilter.ALL) }
    var inspectedTx by remember { mutableStateOf<BillTransaction?>(null) }
    var disputeTx by remember { mutableStateOf<BillTransaction?>(null) }

    val transactions = (transactionsState as? UiState.Success)?.data.orEmpty()
    val grievances = (grievancesState as? UiState.Success)?.data.orEmpty()

    val filteredTransactions = remember(transactions, selectedFilter) {
        when (selectedFilter) {
            LedgerFilter.ALL -> transactions
            LedgerFilter.SETTLED -> transactions.filter { it.statusEnum == TransactionStatus.SETTLED }
            LedgerFilter.PENDING_OR_REFUND -> transactions.filter {
                it.statusEnum != TransactionStatus.SETTLED
            }
        }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "BBPS Ledger & Refunds",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Immutable payment audit trail, T+1 to T+5 auto-refund lifecycle, and Bharat Connect dispute tracking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LedgerFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter.label) },
                                modifier = Modifier.testTag("ledger_filter_${filter.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            when (transactionsState) {
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
                                text = transactionsState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    if (filteredTransactions.isEmpty()) {
                        item {
                            EmptyRailState(
                                icon = Icons.Filled.ReceiptLong,
                                title = "No Transactions Recorded",
                                subtitle = "Completed or pending BBPS bill payments appear here with their B-Assured reference number and dispute controls."
                            )
                        }
                    } else {
                        items(filteredTransactions, key = { it.id }) { tx ->
                            TransactionLedgerRow(
                                tx = tx,
                                onClick = { inspectedTx = tx }
                            )
                        }
                    }
                }
            }

            // Active BBPS Grievances & T+1..T+5 Disputes Section
            item {
                SectionHeader(
                    title = "Grievance & Dispute Redressal",
                    subtitle = "NPCI Bharat Connect complaint log (T+1 to T+5 business days SLA)"
                )
            }

            when (grievancesState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Text(
                            text = grievancesState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is UiState.Success -> {
                    if (grievances.isEmpty()) {
                        item {
                            EmptyRailState(
                                icon = Icons.Filled.SupportAgent,
                                title = "No Open Disputes",
                                subtitle = "Tap any transaction above to inspect its settlement timeline or raise a BBPS complaint quoting its Reference ID."
                            )
                        }
                    } else {
                        items(grievances, key = { it.id }) { ticket ->
                            GrievanceTicketCard(
                                ticket = ticket,
                                onEscalate = {
                                    onUpdateGrievanceStatus(
                                        ticket.id,
                                        GrievanceStatus.ESCALATED,
                                        "Escalated to Nodal Officer / RBI Integrated Ombudsman tier."
                                    )
                                },
                                onResolve = {
                                    onUpdateGrievanceStatus(
                                        ticket.id,
                                        GrievanceStatus.RESOLVED,
                                        "Settlement confirmed / auto-refund credited to source account."
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Transaction Receipt & Settlement Lifecycle Detail Sheet
    if (inspectedTx != null) {
        val currentTx = transactions.firstOrNull { it.id == inspectedTx!!.id } ?: inspectedTx!!
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { inspectedTx = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BBPS Receipt Details",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    TransactionStatusPill(currentTx.statusEnum)
                }

                Text(
                    text = BillerCatalog.formatInrFromPaise(currentTx.amountPaise),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                DetailLine("Biller", currentTx.providerName)
                DetailLine("Category", currentTx.categoryEnum.displayName)
                DetailLine("Masked ID", currentTx.maskedIdentifier)
                DetailLine("BBPS Ref ID", currentTx.bbpsRefId)
                DetailLine("Payment Rail", currentTx.railEnum.title)
                DetailLine("Token Ref", currentTx.tokenRef)
                DetailLine("Timestamp", formatTimestampShort(currentTx.createdAt))

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Valid State Machine Actions matching Firestore Rules
                when (currentTx.statusEnum) {
                    TransactionStatus.PENDING_SETTLEMENT -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    onUpdateTransactionStatus(currentTx.id, TransactionStatus.SETTLED)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Confirm Settled")
                            }
                            OutlinedButton(
                                onClick = {
                                    onUpdateTransactionStatus(
                                        currentTx.id,
                                        TransactionStatus.REFUND_INITIATED
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Initiate Auto-Refund")
                            }
                        }
                    }
                    TransactionStatus.SETTLED -> {
                        OutlinedButton(
                            onClick = {
                                onUpdateTransactionStatus(
                                    currentTx.id,
                                    TransactionStatus.REFUND_INITIATED
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Flag Failed Biller Credit (T+1..T+5 Refund)")
                        }
                    }
                    TransactionStatus.REFUND_INITIATED -> {
                        Button(
                            onClick = {
                                onUpdateTransactionStatus(currentTx.id, TransactionStatus.REFUNDED)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Mark Auto-Refund Credited (Final)")
                        }
                    }
                    TransactionStatus.REFUNDED -> {
                        Text(
                            text = "Terminal state locked: Auto-refund settled to source instrument.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        disputeTx = currentTx
                        inspectedTx = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("raise_dispute_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ReportProblem,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Raise BBPS Dispute / Complaint")
                }
            }
        }
    }

    // Raise BBPS Dispute Sheet
    if (disputeTx != null) {
        RaiseDisputeSheet(
            transaction = disputeTx!!,
            isSubmitting = isSubmitting,
            onDismiss = { disputeTx = null },
            onSubmit = { reason, notes ->
                onRaiseGrievance(disputeTx!!, reason, notes) {
                    disputeTx = null
                }
            }
        )
    }
}

@Composable
private fun TransactionLedgerRow(
    tx: BillTransaction,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("transaction_card_${tx.id}"),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = categoryIcon(tx.categoryEnum),
                            contentDescription = tx.categoryEnum.displayName,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tx.providerName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${tx.maskedIdentifier} · ${tx.bbpsRefId}",
                        style = MonoCodeStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = BillerCatalog.formatInrFromPaise(tx.amountPaise),
                    style = MonoAmountStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${tx.railEnum.title} · ${formatTimestampShort(tx.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TransactionStatusPill(tx.statusEnum)
            }
        }
    }
}

@Composable
private fun TransactionStatusPill(status: TransactionStatus) {
    val (bg, fg) = when (status) {
        TransactionStatus.SETTLED ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.secondary
        TransactionStatus.PENDING_SETTLEMENT ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
        TransactionStatus.REFUND_INITIATED ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
        TransactionStatus.REFUNDED ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
    }
    StatusPill(text = status.label.uppercase(), containerColor = bg, contentColor = fg)
}

@Composable
private fun GrievanceTicketCard(
    ticket: GrievanceTicket,
    onEscalate: () -> Unit,
    onResolve: () -> Unit
) {
    val (bg, fg) = when (ticket.statusEnum) {
        GrievanceStatus.OPEN ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
        GrievanceStatus.ESCALATED ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
        GrievanceStatus.RESOLVED ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.secondary
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("grievance_card_${ticket.id}"),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ticket.complaintRef,
                    style = MonoCodeStyle,
                    color = MaterialTheme.colorScheme.primary
                )
                StatusPill(
                    text = ticket.statusEnum.label.uppercase(),
                    containerColor = bg,
                    contentColor = fg
                )
            }
            Text(
                text = ticket.reasonEnum.label,
                style = MaterialTheme.typography.titleSmall
            )
            if (ticket.notes.isNotBlank()) {
                Text(
                    text = ticket.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "BBPS Ref: ${ticket.bbpsRefId} · SLA: ${ticket.slaWindow}",
                style = MonoCodeStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (ticket.statusEnum != GrievanceStatus.RESOLVED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (ticket.statusEnum == GrievanceStatus.OPEN) {
                        OutlinedButton(
                            onClick = onEscalate,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Escalate Nodal")
                        }
                    }
                    Button(
                        onClick = onResolve,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Mark Resolved")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RaiseDisputeSheet(
    transaction: BillTransaction,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (GrievanceReason, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedReason by remember { mutableStateOf(GrievanceReason.DEBITED_NOT_UPDATED) }
    var notes by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Raise BBPS Complaint",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Ref ${transaction.bbpsRefId} · ${transaction.providerName} (${BillerCatalog.formatInrFromPaise(transaction.amountPaise)})",
                style = MonoCodeStyle,
                color = MaterialTheme.colorScheme.primary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GrievanceReason.entries.forEach { reason ->
                    FilterChip(
                        selected = selectedReason == reason,
                        onClick = { selectedReason = reason },
                        label = { Text(reason.label) }
                    )
                }
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(400) },
                label = { Text("Dispute Details (UTR / Biller Message)") },
                placeholder = { Text("Optional context for BBPOU resolution desk...") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dispute_notes_input")
            )
            Button(
                onClick = { onSubmit(selectedReason, notes) },
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_dispute_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Log Complaint (T+1 to T+5 SLA)")
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MonoCodeStyle,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
