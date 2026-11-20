package com.example.data

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

data class BillerProvider(
    val category: BillCategory,
    val providerName: String,
    val billerCode: String,
    val bbpouPartner: String,
    val region: String
)

data class LegalClause(
    val number: String,
    val title: String,
    val summary: String,
    val body: String
)

data class ComplianceGate(
    val id: String,
    val phase: String,
    val title: String,
    val requirement: String,
    val enforcedInApp: Boolean
)

object BillerCatalog {
    const val CURRENT_TERMS_VERSION = "v1.0-IN"

    val providers: List<BillerProvider> = listOf(
        // Electricity
        BillerProvider(BillCategory.ELECTRICITY, "BESCOM Bengaluru", "BBPS-BESCOM-KA", "NPCI Bharat Connect", "Karnataka"),
        BillerProvider(BillCategory.ELECTRICITY, "Tata Power Mumbai", "BBPS-TATAPWR-MH", "NPCI Bharat Connect", "Maharashtra"),
        BillerProvider(BillCategory.ELECTRICITY, "Adani Electricity Mumbai", "BBPS-AEML-MH", "NPCI Bharat Connect", "Maharashtra"),
        BillerProvider(BillCategory.ELECTRICITY, "BSES Rajdhani Power", "BBPS-BRPL-DL", "NPCI Bharat Connect", "Delhi NCR"),
        BillerProvider(BillCategory.ELECTRICITY, "MSEDCL Mahavitaran", "BBPS-MSEDCL-MH", "NPCI Bharat Connect", "Maharashtra"),
        BillerProvider(BillCategory.ELECTRICITY, "TANGEDCO (TNEB)", "BBPS-TNEB-TN", "NPCI Bharat Connect", "Tamil Nadu"),

        // Credit Card (BBPS Credit Card Bill Pay — Last 4 digits only)
        BillerProvider(BillCategory.CREDIT_CARD, "HDFC Bank Credit Card", "BBPS-HDFC-CC", "RBI Tokenized BBPOU", "Pan-India"),
        BillerProvider(BillCategory.CREDIT_CARD, "ICICI Bank Credit Card", "BBPS-ICICI-CC", "RBI Tokenized BBPOU", "Pan-India"),
        BillerProvider(BillCategory.CREDIT_CARD, "SBI Card", "BBPS-SBICARD-CC", "RBI Tokenized BBPOU", "Pan-India"),
        BillerProvider(BillCategory.CREDIT_CARD, "Axis Bank Credit Card", "BBPS-AXIS-CC", "RBI Tokenized BBPOU", "Pan-India"),
        BillerProvider(BillCategory.CREDIT_CARD, "Kotak Mahindra Card", "BBPS-KOTAK-CC", "RBI Tokenized BBPOU", "Pan-India"),

        // Postpaid
        BillerProvider(BillCategory.POSTPAID, "Airtel Postpaid", "BBPS-AIRTEL-POST", "NPCI Bharat Connect", "Pan-India"),
        BillerProvider(BillCategory.POSTPAID, "Jio Postpaid", "BBPS-JIO-POST", "NPCI Bharat Connect", "Pan-India"),
        BillerProvider(BillCategory.POSTPAID, "Vi (Vodafone Idea) Postpaid", "BBPS-VI-POST", "NPCI Bharat Connect", "Pan-India"),
        BillerProvider(BillCategory.POSTPAID, "BSNL Mobile Postpaid", "BBPS-BSNL-POST", "NPCI Bharat Connect", "Pan-India"),

        // Prepaid
        BillerProvider(BillCategory.PREPAID, "Jio Prepaid", "BBPS-JIO-PRE", "NPCI Bharat Connect", "Pan-India"),
        BillerProvider(BillCategory.PREPAID, "Airtel Prepaid", "BBPS-AIRTEL-PRE", "NPCI Bharat Connect", "Pan-India"),
        BillerProvider(BillCategory.PREPAID, "Vi Prepaid", "BBPS-VI-PRE", "NPCI Bharat Connect", "Pan-India"),
        BillerProvider(BillCategory.PREPAID, "BSNL Prepaid", "BBPS-BSNL-PRE", "NPCI Bharat Connect", "Pan-India"),

        // FASTag
        BillerProvider(BillCategory.FASTAG, "NHAI IHMCL FASTag", "BBPS-NHAI-FTAG", "NETC / Bharat Connect", "National Highways"),
        BillerProvider(BillCategory.FASTAG, "ICICI Bank FASTag", "BBPS-ICICI-FTAG", "NETC / Bharat Connect", "National Highways"),
        BillerProvider(BillCategory.FASTAG, "HDFC Bank FASTag", "BBPS-HDFC-FTAG", "NETC / Bharat Connect", "National Highways"),
        BillerProvider(BillCategory.FASTAG, "IDFC FIRST FASTag", "BBPS-IDFC-FTAG", "NETC / Bharat Connect", "National Highways"),
        BillerProvider(BillCategory.FASTAG, "Axis Bank FASTag", "BBPS-AXIS-FTAG", "NETC / Bharat Connect", "National Highways")
    )

    fun providersFor(category: BillCategory): List<BillerProvider> =
        providers.filter { it.category == category }

    fun formatInrFromPaise(paise: Long): String {
        val rupees = paise / 100.0
        val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        formatter.maximumFractionDigits = if (paise % 100L == 0L) 0 else 2
        formatter.minimumFractionDigits = if (paise % 100L == 0L) 0 else 2
        return formatter.format(rupees)
    }

    /**
     * Enforces Zero Raw PANs:
     * - For CREDIT_CARD, strictly extracts only the last 4 digits and returns "•••• XXXX".
     * - For other categories, rejects any 13-19 digit raw card number string.
     */
    fun sanitizeIdentifier(category: BillCategory, rawInput: String): Result<String> {
        val trimmed = rawInput.trim()
        if (category == BillCategory.CREDIT_CARD) {
            val digitsOnly = trimmed.filter { it.isDigit() }
            if (digitsOnly.length != 4) {
                return Result.failure(
                    IllegalArgumentException("Enter only the last 4 digits of your card. Full card numbers are never stored.")
                )
            }
            return Result.success("•••• $digitsOnly")
        }

        if (trimmed.length < 4 || trimmed.length > 32) {
            return Result.failure(
                IllegalArgumentException("Identifier must be between 4 and 32 characters.")
            )
        }

        val rawPanRegex = Regex("^[0-9\\s\\-]{13,23}$")
        if (rawPanRegex.matches(trimmed)) {
            return Result.failure(
                IllegalArgumentException("Raw 13–19 digit card numbers are prohibited on this rail.")
            )
        }

        val formatted = when (category) {
            BillCategory.FASTAG -> trimmed.uppercase(Locale.ROOT).replace(" ", "")
            BillCategory.POSTPAID, BillCategory.PREPAID -> {
                val digits = trimmed.filter { it.isDigit() }
                if (digits.length != 10) {
                    return Result.failure(
                        IllegalArgumentException("Enter a valid 10-digit Indian mobile number.")
                    )
                }
                "+91 ${digits.substring(0, 5)} ${digits.substring(5)}"
            }
            else -> trimmed
        }
        return Result.success(formatted)
    }

    fun generateBbpsRefId(seed: String): String {
        val hash = abs((seed + System.currentTimeMillis()).hashCode())
            .toString(36)
            .uppercase(Locale.ROOT)
            .padStart(6, '0')
            .take(6)
        return "BP2611$hash"
    }

    fun generateComplaintRef(seed: String): String {
        val hash = abs((seed + System.nanoTime()).hashCode())
            .toString(36)
            .uppercase(Locale.ROOT)
            .padStart(6, '0')
            .take(6)
        return "CRN2611$hash"
    }

    fun generateTokenRef(rail: PaymentRail, seed: String): String {
        val suffix = abs((seed + System.currentTimeMillis()).hashCode())
            .toString(16)
            .lowercase(Locale.ROOT)
            .padStart(8, '0')
            .take(8)
        return when (rail) {
            PaymentRail.UPI_INTENT -> "upi_intent_$suffix"
            PaymentRail.TOKENIZED_PA -> "tok_rbi_$suffix"
        }
    }

    val legalClauses: List<LegalClause> = listOf(
        LegalClause(
            number = "01",
            title = "Regulated Rail Intermediary & BBPOU Agent Role",
            summary = "BillRail operates strictly as an interface layer over NPCI Bharat Connect (BBPS) Operating Units and RBI-authorized Payment Aggregators.",
            body = "BillRail does not hold customer funds, operate a prepaid payment instrument, or directly settle inter-bank balances. All electricity, credit card, telecom postpaid/prepaid, and NETC FASTag bill fetches and settlements are executed via a licensed Bharat Bill Payment Operating Unit (BBPOU) and RBI-authorized Payment Aggregator SDKs or NPCI UPI Intent rails."
        ),
        LegalClause(
            number = "02",
            title = "Zero Card-Vault & RBI Tokenization Mandate",
            summary = "No raw 16-digit PANs, CVVs, or UPI PINs are ever collected, cached, or stored on BillRail servers.",
            body = "In strict compliance with RBI Card-on-File Tokenization (CoFT) guidelines, BillRail stores only masked last-4 credit card identifiers (e.g., •••• 4821) and non-sensitive consumer numbers. Sensitive authentication (UPI PIN, 3DS OTP, network token vaulting) occurs exclusively within your bank, UPI app, or PCI-DSS Level 1 Payment Aggregator SDK."
        ),
        LegalClause(
            number = "03",
            title = "Settlement Windows & T+1 to T+5 Auto-Refund Policy",
            summary = "Failed or unconfirmed BBPS debits trigger automated source-account reversals within T+1 to T+5 business days.",
            body = "Once a biller confirms settlement via BBPS B-Assured confirmation, the transaction is final and non-reversible. If your bank account is debited via UPI or tokenized checkout but the biller rejects or times out on settlement, the Payment Aggregator / NPCI Harmonised TAT framework initiates an automatic refund to the original source instrument within T+1 to T+5 business days."
        ),
        LegalClause(
            number = "04",
            title = "BBPS Dispute Resolution & Statutory Grievance Redressal",
            summary = "Every transaction carries a unique BBPS Reference ID eligible for formal Bharat Connect complaint tracking.",
            body = "Users may raise a transaction dispute directly from the Ledger screen quoting the BBPS Reference ID. Level 1 complaints are logged with the BBPOU for resolution within 5 working days. Unresolved grievances may be escalated to the designated Nodal Grievance Officer and subsequently to the RBI Integrated Ombudsman Scheme (CMS Portal) after 30 days."
        ),
        LegalClause(
            number = "05",
            title = "Data Localization (RBI) & DPDP Act 2023 Consent",
            summary = "Payment metadata is strictly isolated per authenticated user with explicit consent under India's Digital Personal Data Protection Act.",
            body = "Transaction metadata, saved biller labels, and consent timestamps are protected under per-user zero-trust access rules and retained solely for bill payment execution, regulatory audit trails, and statutory dispute resolution. BillRail never requests broad media/storage, SMS-read, or contact-scraping permissions."
        ),
        LegalClause(
            number = "06",
            title = "User Accuracy, Governing Law & Jurisdiction",
            summary = "Users are responsible for verifying consumer/vehicle identifiers prior to checkout; governed by laws of India.",
            body = "Users must verify the fetched consumer name, biller code, and amount before authorizing UPI or tokenized checkout. BillRail is not liable for biller-side tariff disputes or payments directed to an erroneous consumer number confirmed by the user. These Terms are governed by the laws of India, subject to the exclusive jurisdiction of competent Indian courts."
        )
    )

    val complianceGates: List<ComplianceGate> = listOf(
        ComplianceGate(
            id = "zero_pan",
            phase = "PCI / RBI CoFT",
            title = "Zero Raw PAN & CVV Storage",
            requirement = "Enforced in UI input validator and Firestore Security Rules regex guard.",
            enforcedInApp = true
        ),
        ComplianceGate(
            id = "least_privilege",
            phase = "Play Policy",
            title = "Minimal Permissions (No SMS / Storage / Contacts)",
            requirement = "Uses only INTERNET, ACCESS_NETWORK_STATE, and zero-permission upi:// query.",
            enforcedInApp = true
        ),
        ComplianceGate(
            id = "backup_disabled",
            phase = "OWASP MASVS",
            title = "Android Cloud Backup Disabled (allowBackup=false)",
            requirement = "Prevents unencrypted local ADB or backup extraction of fintech app state.",
            enforcedInApp = true
        ),
        ComplianceGate(
            id = "flag_secure",
            phase = "Layer 1 Defense",
            title = "FLAG_SECURE Screen Shield Control",
            requirement = "Blocks unauthorized screenshots and recents-tray thumbnails on sensitive bill screens.",
            enforcedInApp = true
        ),
        ComplianceGate(
            id = "play_closed_test",
            phase = "Play Console",
            title = "12-Tester / 14-Day Closed Test & Financial Declaration",
            requirement = "Required before production rollout on new personal Play Console accounts.",
            enforcedInApp = false
        ),
        ComplianceGate(
            id = "bbpou_kyb",
            phase = "NPCI / RBI",
            title = "BBPOU Agent Institution & RBI PA Onboarding",
            requirement = "Bind production API credentials behind Cloud WAF + TLS Certificate Pinning.",
            enforcedInApp = false
        )
    )
}
