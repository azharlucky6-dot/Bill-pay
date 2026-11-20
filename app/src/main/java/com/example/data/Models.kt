package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class BillCategory(
    val code: String,
    val displayName: String,
    val identifierLabel: String,
    val identifierHint: String
) {
    ELECTRICITY(
        code = "ELECTRICITY",
        displayName = "Electricity",
        identifierLabel = "Consumer / CA Number",
        identifierHint = "e.g. KA-90482103"
    ),
    CREDIT_CARD(
        code = "CREDIT_CARD",
        displayName = "Credit Card",
        identifierLabel = "Last 4 Digits Only (Zero Raw PAN)",
        identifierHint = "4821"
    ),
    POSTPAID(
        code = "POSTPAID",
        displayName = "Postpaid",
        identifierLabel = "10-Digit Mobile Number",
        identifierHint = "9876543210"
    ),
    PREPAID(
        code = "PREPAID",
        displayName = "Prepaid",
        identifierLabel = "10-Digit Mobile Number",
        identifierHint = "9876543210"
    ),
    FASTAG(
        code = "FASTAG",
        displayName = "FASTag",
        identifierLabel = "Vehicle Registration Number",
        identifierHint = "KA01AB1234"
    );

    companion object {
        fun fromCode(raw: String): BillCategory =
            entries.firstOrNull { it.code == raw } ?: ELECTRICITY
    }
}

enum class PaymentRail(
    val code: String,
    val title: String,
    val subtitle: String,
    val feePaise: Long
) {
    UPI_INTENT(
        code = "UPI_INTENT",
        title = "UPI Intent Rail",
        subtitle = "Zero-MDR NPCI UPI checkout via installed UPI apps",
        feePaise = 0L
    ),
    TOKENIZED_PA(
        code = "TOKENIZED_PA",
        title = "RBI Tokenized PA SDK",
        subtitle = "Network token checkout via RBI-authorized Payment Aggregator",
        feePaise = 0L
    );

    companion object {
        fun fromCode(raw: String): PaymentRail =
            entries.firstOrNull { it.code == raw } ?: UPI_INTENT
    }
}

enum class TransactionStatus(val code: String, val label: String) {
    SETTLED("SETTLED", "Settled"),
    PENDING_SETTLEMENT("PENDING_SETTLEMENT", "Pending T+1"),
    REFUND_INITIATED("REFUND_INITIATED", "Refund T+1..T+5"),
    REFUNDED("REFUNDED", "Refunded");

    companion object {
        fun fromCode(raw: String): TransactionStatus =
            entries.firstOrNull { it.code == raw } ?: PENDING_SETTLEMENT
    }
}

enum class GrievanceReason(val code: String, val label: String) {
    DEBITED_NOT_UPDATED("DEBITED_NOT_UPDATED", "Amount debited, biller not updated"),
    DUPLICATE_PAYMENT("DUPLICATE_PAYMENT", "Duplicate bill debit"),
    WRONG_BILLER_CREDIT("WRONG_BILLER_CREDIT", "Incorrect biller settlement"),
    REFUND_DELAYED("REFUND_DELAYED", "Auto-refund delayed past T+5 days");

    companion object {
        fun fromCode(raw: String): GrievanceReason =
            entries.firstOrNull { it.code == raw } ?: DEBITED_NOT_UPDATED
    }
}

enum class GrievanceStatus(val code: String, val label: String) {
    OPEN("OPEN", "Open (BBPOU)"),
    ESCALATED("ESCALATED", "Escalated (Nodal/RBI)"),
    RESOLVED("RESOLVED", "Resolved");

    companion object {
        fun fromCode(raw: String): GrievanceStatus =
            entries.firstOrNull { it.code == raw } ?: OPEN
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val termsAccepted: Boolean = false,
    val termsVersion: String = "",
    val screenShieldEnabled: Boolean = false,
    val biometricConfirmEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BillTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val billerId: String = "direct",
    val category: String = BillCategory.ELECTRICITY.code,
    val providerName: String = "",
    val maskedIdentifier: String = "",
    val amountPaise: Long = 0L,
    val convenienceFeePaise: Long = 0L,
    val paymentRail: String = PaymentRail.UPI_INTENT.code,
    val tokenRef: String = "",
    val bbpsRefId: String = "",
    val status: String = TransactionStatus.SETTLED.code,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val categoryEnum: BillCategory get() = BillCategory.fromCode(category)
    val railEnum: PaymentRail get() = PaymentRail.fromCode(paymentRail)
    val statusEnum: TransactionStatus get() = TransactionStatus.fromCode(status)
}

@Keep
@IgnoreExtraProperties
data class GrievanceTicket(
    @DocumentId val id: String = "",
    val userId: String = "",
    val transactionId: String = "",
    val bbpsRefId: String = "",
    val complaintRef: String = "",
    val reason: String = GrievanceReason.DEBITED_NOT_UPDATED.code,
    val notes: String = "",
    val slaWindow: String = "T+1 to T+5 Business Days",
    val status: String = GrievanceStatus.OPEN.code,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val reasonEnum: GrievanceReason get() = GrievanceReason.fromCode(reason)
    val statusEnum: GrievanceStatus get() = GrievanceStatus.fromCode(status)
}
