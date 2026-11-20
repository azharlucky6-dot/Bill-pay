package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

/**
 * Firestore data model representing a user-saved BBPS biller stored under
 * `/users/{userId}/billers/{billerId}`.
 *
 * Security invariants:
 * - Annotated with @Keep and @IgnoreExtraProperties for R8/schema safety.
 * - Uses @DocumentId so Firestore maps snapshot.id automatically and never writes an "id" field.
 * - Stores only masked identifiers (e.g., "•••• 4821" for credit cards); raw PANs are prohibited.
 */
@Keep
@IgnoreExtraProperties
data class Biller(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = BillCategory.ELECTRICITY.code,
    val providerName: String = "",
    val billerCode: String = "",
    val nickname: String = "",
    val maskedIdentifier: String = "",
    val lastBillAmountPaise: Long = 0L,
    val dueDayOfMonth: Long = 15L,
    val autopayReminder: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val categoryEnum: BillCategory
        get() = BillCategory.fromCode(category)

    /**
     * Builds a Firestore create payload aligned 1:1 with `isValidSavedBiller` in `firestore.rules`.
     * Omits `id` (mapped via @DocumentId) and always supplies server timestamps.
     */
    fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "category" to category,
        "providerName" to providerName.trim().take(80),
        "billerCode" to billerCode.trim().take(40),
        "nickname" to nickname.trim().ifBlank { providerName }.take(60),
        "maskedIdentifier" to maskedIdentifier.trim().take(32),
        "lastBillAmountPaise" to lastBillAmountPaise.coerceIn(0L, 100_000_000L),
        "dueDayOfMonth" to dueDayOfMonth.coerceIn(1L, 31L),
        "autopayReminder" to autopayReminder,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
