package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.BillCategory
import com.example.data.BillTransaction
import com.example.data.Biller
import com.example.data.BillerCatalog
import com.example.data.BillerProvider
import com.example.data.PaymentRail
import com.example.security.DeviceSecurityInspector
import com.example.ui.components.StatusPill
import com.example.ui.theme.MonoAmountStyle
import com.example.ui.theme.MonoCodeStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddBillerSheet(
    initialCategory: BillCategory,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        category: BillCategory,
        provider: BillerProvider,
        nickname: String,
        rawIdentifier: String,
        amountRupeesText: String,
        dueDay: Int,
        autopayReminder: Boolean
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCategory by remember(initialCategory) { mutableStateOf(initialCategory) }
    val providers = remember(selectedCategory) { BillerCatalog.providersFor(selectedCategory) }
    var selectedProvider by remember(selectedCategory) { mutableStateOf(providers.first()) }
    var nickname by remember { mutableStateOf("") }
    var rawIdentifier by remember { mutableStateOf("") }
    var expectedAmount by remember { mutableStateOf("") }
    var dueDayText by remember { mutableStateOf("15") }
    var autopayReminder by remember { mutableStateOf(true) }
    var validationError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Add Regulated BBPS Biller",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Category Chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BillCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = {
                            selectedCategory = cat
                            selectedProvider = BillerCatalog.providersFor(cat).first()
                            rawIdentifier = ""
                            validationError = null
                        },
                        label = { Text(cat.displayName) }
                    )
                }
            }

            // Zero Raw PAN Safety Banner for Credit Cards
            if (selectedCategory == BillCategory.CREDIT_CARD) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Zero Card-Vault Guard: Enter only the last 4 card digits. Full 16-digit PANs and CVVs are blocked by app validation and Firestore rules.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Provider Selector
            Text(
                text = "Select BBPS Provider",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                providers.forEach { provider ->
                    FilterChip(
                        selected = selectedProvider.billerCode == provider.billerCode,
                        onClick = { selectedProvider = provider },
                        label = { Text(provider.providerName) }
                    )
                }
            }

            OutlinedTextField(
                value = rawIdentifier,
                onValueChange = { input ->
                    rawIdentifier = if (selectedCategory == BillCategory.CREDIT_CARD) {
                        input.filter { it.isDigit() }.take(4)
                    } else {
                        input.take(32)
                    }
                    validationError = null
                },
                label = { Text(selectedCategory.identifierLabel) },
                placeholder = { Text(selectedCategory.identifierHint) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (selectedCategory in listOf(
                            BillCategory.CREDIT_CARD,
                            BillCategory.POSTPAID,
                            BillCategory.PREPAID
                        )
                    ) KeyboardType.Number else KeyboardType.Text
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("biller_identifier_input")
            )

            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it.take(60) },
                label = { Text("Nickname (Optional)") },
                placeholder = { Text("e.g. Home Power / Primary Card") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("biller_nickname_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = expectedAmount,
                    onValueChange = { expectedAmount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(9) },
                    label = { Text("Typical Bill (₹)") },
                    placeholder = { Text("1850") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1.3f)
                )
                OutlinedTextField(
                    value = dueDayText,
                    onValueChange = { dueDayText = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text("Due Day (1-31)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Monthly Due Reminder", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Highlight this biller before due day",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autopayReminder,
                    onCheckedChange = { autopayReminder = it }
                )
            }

            if (validationError != null) {
                Text(
                    text = validationError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    val sanitized = BillerCatalog.sanitizeIdentifier(selectedCategory, rawIdentifier)
                    if (sanitized.isFailure) {
                        validationError = sanitized.exceptionOrNull()?.message
                        return@Button
                    }
                    val dueDay = (dueDayText.toIntOrNull() ?: 15).coerceIn(1, 31)
                    onSave(
                        selectedCategory,
                        selectedProvider,
                        nickname,
                        rawIdentifier,
                        expectedAmount,
                        dueDay,
                        autopayReminder
                    )
                },
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_biller_submit_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Save Biller to BillRail")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditBillerSheet(
    biller: Biller,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onUpdate: (
        billerId: String,
        nickname: String,
        amountRupeesText: String,
        dueDay: Int,
        autopayReminder: Boolean
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var nickname by remember(biller) { mutableStateOf(biller.nickname) }
    var expectedAmount by remember(biller) {
        mutableStateOf(
            if (biller.lastBillAmountPaise > 0) (biller.lastBillAmountPaise / 100L).toString() else ""
        )
    }
    var dueDayText by remember(biller) { mutableStateOf(biller.dueDayOfMonth.toString()) }
    var autopayReminder by remember(biller) { mutableStateOf(biller.autopayReminder) }
    var validationError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Edit Saved Biller",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = biller.providerName,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "${biller.maskedIdentifier} · ${biller.billerCode}",
                        style = MonoCodeStyle,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            OutlinedTextField(
                value = nickname,
                onValueChange = {
                    nickname = it.take(60)
                    validationError = null
                },
                label = { Text("Nickname") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_biller_nickname_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = expectedAmount,
                    onValueChange = {
                        expectedAmount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(9)
                    },
                    label = { Text("Typical Bill (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1.3f)
                )
                OutlinedTextField(
                    value = dueDayText,
                    onValueChange = { dueDayText = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text("Due Day (1-31)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Monthly Due Reminder", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Highlight this biller before due day",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autopayReminder,
                    onCheckedChange = { autopayReminder = it }
                )
            }

            if (validationError != null) {
                Text(
                    text = validationError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    if (nickname.trim().isEmpty()) {
                        validationError = "Enter a nickname for this biller."
                        return@Button
                    }
                    val dueDay = (dueDayText.toIntOrNull() ?: 15).coerceIn(1, 31)
                    onUpdate(biller.id, nickname.trim(), expectedAmount, dueDay, autopayReminder)
                },
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("update_biller_submit_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Save Changes")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BillCheckoutSheet(
    savedBiller: Biller?,
    initialCategory: BillCategory,
    biometricConfirmEnabled: Boolean,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmitPayment: (
        billerId: String,
        category: BillCategory,
        providerName: String,
        identifier: String,
        alreadyMasked: Boolean,
        amountRupeesText: String,
        paymentRail: PaymentRail,
        markPendingSettlement: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val category = savedBiller?.categoryEnum ?: initialCategory
    val providers = remember(category) { BillerCatalog.providersFor(category) }
    var selectedProvider by remember(savedBiller, category) {
        mutableStateOf(
            providers.firstOrNull { it.providerName == savedBiller?.providerName } ?: providers.first()
        )
    }
    var rawIdentifier by remember(savedBiller) {
        mutableStateOf(savedBiller?.maskedIdentifier ?: "")
    }
    var amountRupees by remember(savedBiller) {
        mutableStateOf(
            if (savedBiller != null && savedBiller.lastBillAmountPaise > 0) {
                (savedBiller.lastBillAmountPaise / 100L).toString()
            } else {
                ""
            }
        )
    }
    var selectedRail by remember { mutableStateOf(PaymentRail.UPI_INTENT) }
    var markPending by remember { mutableStateOf(false) }
    var securityConfirmed by remember { mutableStateOf(!biometricConfirmEnabled) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val upiAppsInstalled by produceState(initialValue = 0, key1 = context) {
        value = withContext(Dispatchers.IO) {
            DeviceSecurityInspector.queryInstalledUpiApps(context)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "BBPS Bill Checkout",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = category.displayName + " · " + selectedProvider.billerCode,
                        style = MonoCodeStyle,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                StatusPill(
                    text = "₹0 FEE",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.secondary
                )
            }

            if (savedBiller == null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    providers.forEach { provider ->
                        FilterChip(
                            selected = selectedProvider.billerCode == provider.billerCode,
                            onClick = { selectedProvider = provider },
                            label = { Text(provider.providerName) }
                        )
                    }
                }
                OutlinedTextField(
                    value = rawIdentifier,
                    onValueChange = { input ->
                        rawIdentifier = if (category == BillCategory.CREDIT_CARD) {
                            input.filter { it.isDigit() }.take(4)
                        } else {
                            input.take(32)
                        }
                        errorMsg = null
                    },
                    label = { Text(category.identifierLabel) },
                    placeholder = { Text(category.identifierHint) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("checkout_identifier_input")
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = savedBiller.nickname,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${savedBiller.providerName} · ${savedBiller.maskedIdentifier}",
                            style = MonoCodeStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedTextField(
                value = amountRupees,
                onValueChange = {
                    amountRupees = it.filter { ch -> ch.isDigit() || ch == '.' }.take(9)
                    errorMsg = null
                },
                label = { Text("Bill Amount (INR ₹)") },
                placeholder = { Text("e.g. 2480") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("checkout_amount_input")
            )

            // Payment Rail Selection
            Text(
                text = "Select Regulated Checkout Rail",
                style = MaterialTheme.typography.labelLarge
            )
            PaymentRail.entries.forEach { rail ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedRail = rail },
                    color = if (selectedRail == rail) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(
                        width = if (selectedRail == rail) 1.5.dp else 1.dp,
                        color = if (selectedRail == rail) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedRail == rail,
                            onClick = { selectedRail = rail }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rail.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                rail.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Real Android UPI Intent Launcher when UPI Intent rail selected
            if (selectedRail == PaymentRail.UPI_INTENT) {
                OutlinedButton(
                    onClick = {
                        val amtVal = amountRupees.toDoubleOrNull()
                        if (amtVal == null || amtVal < 1.0) {
                            errorMsg = "Enter a valid INR amount before launching UPI."
                            return@OutlinedButton
                        }
                        val upiIntent = DeviceSecurityInspector.buildUpiPaymentIntent(
                            payeeName = selectedProvider.providerName,
                            billerCode = selectedProvider.billerCode,
                            amountRupees = String.format(java.util.Locale.US, "%.2f", amtVal),
                            transactionNote = "BBPS ${category.displayName}"
                        )
                        try {
                            context.startActivity(upiIntent)
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(
                                context,
                                "No UPI app installed on this device. You can still record checkout via Tokenized PA or confirm below.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("launch_upi_intent_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Launch,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (upiAppsInstalled > 0) {
                            "Open Installed UPI App ($upiAppsInstalled found)"
                        } else {
                            "Launch UPI Intent (upi://pay)"
                        }
                    )
                }
            }

            // Settlement State Option (lets user test T+1 pending / auto-refund tracking too)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Log as Pending T+1 Settlement", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Track awaiting biller confirmation & T+1..T+5 refund SLA",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = markPending,
                    onCheckedChange = { markPending = it }
                )
            }

            if (biometricConfirmEnabled) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { securityConfirmed = !securityConfirmed }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = securityConfirmed,
                            onCheckedChange = { securityConfirmed = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "I verified the biller code & masked ID and authorize regulated rail checkout.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (errorMsg != null) {
                Text(
                    text = errorMsg!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    if (biometricConfirmEnabled && !securityConfirmed) {
                        errorMsg = "Confirm biller details authorization before continuing."
                        return@Button
                    }
                    onSubmitPayment(
                        savedBiller?.id ?: "direct",
                        category,
                        selectedProvider.providerName,
                        rawIdentifier,
                        savedBiller != null,
                        amountRupees,
                        selectedRail,
                        markPending
                    )
                },
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_pay_bill_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Confirm & Generate BBPS Receipt")
            }
        }
    }
}

@Composable
fun ReceiptDialog(
    transaction: BillTransaction,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Receipt Recorded",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(34.dp)
            )
        },
        title = {
            Text(
                text = "BBPS B-Assured Receipt",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = BillerCatalog.formatInrFromPaise(transaction.amountPaise),
                    style = MonoAmountStyle,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                ReceiptRow("Biller", transaction.providerName)
                ReceiptRow("Masked ID", transaction.maskedIdentifier)
                ReceiptRow("BBPS Ref", transaction.bbpsRefId)
                ReceiptRow("Rail", transaction.railEnum.title)
                ReceiptRow("Token Ref", transaction.tokenRef)
                ReceiptRow("Status", transaction.statusEnum.label)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun ReceiptRow(label: String, value: String) {
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
