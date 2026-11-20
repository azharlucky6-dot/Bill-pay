package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Dedicated Firestore repository for managing user-owned [Biller] documents under
 * `/users/{userId}/billers/{billerId}`.
 */
class BillerRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    // Always initialize with the provisioned enterprise Firestore database ID
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing billers.")
    }

    private fun billersCollection(uid: String) =
        db.collection("users").document(uid).collection("billers")

    /**
     * Real-time stream of the authenticated user's saved billers ordered by monthly due day.
     */
    fun observeBillers(userId: String): Flow<List<Biller>> = flow {
        val path = "users/$userId/billers"
        emitAll(
            billersCollection(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Biller::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.dueDayOfMonth }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    /**
     * One-shot fetch of a single biller owned by [userId].
     */
    suspend fun getBiller(userId: String, billerId: String): Result<Biller?> {
        val docPath = "users/$userId/billers/$billerId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user read." }
            require(billerId.isNotBlank()) { "Biller ID must not be blank." }
            val snapshot = billersCollection(uid).document(billerId).get().await()
            val biller = if (snapshot.exists()) {
                snapshot.toObject(
                    Biller::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else {
                null
            }
            Result.success(biller)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    /**
     * Validates and creates a new [Biller] document under `/users/{uid}/billers/{billerId}`.
     * Enforces Zero Raw PANs via [BillerCatalog.sanitizeIdentifier].
     */
    suspend fun addBiller(
        category: BillCategory,
        provider: BillerProvider,
        nickname: String,
        rawIdentifier: String,
        expectedAmountPaise: Long,
        dueDayOfMonth: Int,
        autopayReminder: Boolean
    ): Result<String> {
        var docPath = "users/unauthenticated/billers"
        return try {
            val uid = requireUserId()
            val sanitizedIdentifier = BillerCatalog.sanitizeIdentifier(category, rawIdentifier).getOrThrow()
            val biller = Biller(
                userId = uid,
                category = category.code,
                providerName = provider.providerName.trim().take(80),
                billerCode = provider.billerCode.trim().take(40),
                nickname = nickname.trim().ifBlank { provider.providerName }.take(60),
                maskedIdentifier = sanitizedIdentifier,
                lastBillAmountPaise = expectedAmountPaise.coerceIn(0L, 100_000_000L),
                dueDayOfMonth = dueDayOfMonth.coerceIn(1, 31).toLong(),
                autopayReminder = autopayReminder
            )
            val docRef = billersCollection(uid).document()
            docPath = docRef.path
            docRef.set(biller.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    /**
     * Partial update of mutable biller metadata (nickname, expected amount, due day, reminder).
     * Preserves immutable fields (`userId`, `createdAt`, `category`, `maskedIdentifier`).
     */
    suspend fun updateBiller(
        billerId: String,
        nickname: String,
        lastBillAmountPaise: Long,
        dueDayOfMonth: Int,
        autopayReminder: Boolean
    ): Result<Unit> {
        var docPath = "users/unauthenticated/billers/$billerId"
        return try {
            val uid = requireUserId()
            require(billerId.isNotBlank()) { "Biller ID must not be blank." }
            val cleanNickname = nickname.trim().take(60)
            require(cleanNickname.isNotEmpty()) { "Nickname must not be empty." }
            val docRef = billersCollection(uid).document(billerId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "nickname" to cleanNickname,
                    "lastBillAmountPaise" to lastBillAmountPaise.coerceIn(0L, 100_000_000L),
                    "dueDayOfMonth" to dueDayOfMonth.coerceIn(1, 31).toLong(),
                    "autopayReminder" to autopayReminder,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    /**
     * Partial update refreshing only `lastBillAmountPaise` and `updatedAt`.
     */
    suspend fun refreshBillAmount(billerId: String, amountPaise: Long): Result<Unit> {
        var docPath = "users/unauthenticated/billers/$billerId"
        return try {
            val uid = requireUserId()
            require(billerId.isNotBlank()) { "Biller ID must not be blank." }
            val docRef = billersCollection(uid).document(billerId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "lastBillAmountPaise" to amountPaise.coerceIn(0L, 100_000_000L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    /**
     * Deletes a saved biller owned by the authenticated user.
     */
    suspend fun deleteBiller(billerId: String): Result<Unit> {
        var docPath = "users/unauthenticated/billers/$billerId"
        return try {
            val uid = requireUserId()
            require(billerId.isNotBlank()) { "Biller ID must not be blank." }
            val docRef = billersCollection(uid).document(billerId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
