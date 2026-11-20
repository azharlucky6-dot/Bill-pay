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

class BillRailRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val billerRepository: BillerRepository = BillerRepository(db, auth)
) {
    // CRITICAL: Always initialize with the provisioned enterprise database ID
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)
    private fun transactionsCol(uid: String) = userDoc(uid).collection("transactions")
    private fun grievancesCol(uid: String) = userDoc(uid).collection("grievances")

    // --- REALTIME OBSERVATION FLOWS ---

    fun observeProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            userDoc(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    fun observeTransactions(userId: String): Flow<List<BillTransaction>> = flow {
        val path = "users/$userId/transactions"
        emitAll(
            transactionsCol(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        BillTransaction::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeGrievances(userId: String): Flow<List<GrievanceTicket>> = flow {
        val path = "users/$userId/grievances"
        emitAll(
            grievancesCol(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        GrievanceTicket::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    private fun logStreamErrorIfActive(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }

    // --- PROFILE & LEGAL CONSENT MUTATIONS ---

    suspend fun ensureUserProfile(displayName: String, email: String): Result<Unit> {
        var docPath = "users/unauthenticated"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid)
            docPath = docRef.path
            val existing = docRef.get().await()
            val safeName = displayName.trim().ifBlank { "BillRail User" }.take(80)
            val safeEmail = email.trim().take(120)

            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "termsAccepted" to false,
                    "termsVersion" to "",
                    "screenShieldEnabled" to false,
                    "biometricConfirmEnabled" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun acceptTerms(version: String = BillerCatalog.CURRENT_TERMS_VERSION): Result<Unit> {
        var docPath = "users/unauthenticated"
        return try {
            val uid = requireUserId()
            val cleanVersion = version.trim().take(20)
            require(cleanVersion.length >= 2) { "Invalid terms version." }
            val docRef = userDoc(uid)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "termsAccepted" to true,
                    "termsVersion" to cleanVersion,
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

    suspend fun updateSecurityPreferences(
        screenShieldEnabled: Boolean,
        biometricConfirmEnabled: Boolean
    ): Result<Unit> {
        var docPath = "users/unauthenticated"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "screenShieldEnabled" to screenShieldEnabled,
                    "biometricConfirmEnabled" to biometricConfirmEnabled,
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

    // --- BILL PAYMENT TRANSACTION MUTATIONS ---

    suspend fun recordBillPayment(
        billerId: String,
        category: BillCategory,
        providerName: String,
        maskedIdentifier: String,
        amountPaise: Long,
        paymentRail: PaymentRail,
        initialStatus: TransactionStatus = TransactionStatus.SETTLED
    ): Result<BillTransaction> {
        var docPath = "users/unauthenticated/transactions"
        return try {
            val uid = requireUserId()
            require(amountPaise in 100L..100_000_000L) {
                "Amount must be between ₹1 and ₹10,00,000."
            }
            require(
                initialStatus == TransactionStatus.SETTLED ||
                    initialStatus == TransactionStatus.PENDING_SETTLEMENT
            ) {
                "Invalid initial transaction status."
            }

            val docRef = transactionsCol(uid).document()
            docPath = docRef.path
            val bbpsRefId = BillerCatalog.generateBbpsRefId(docRef.id)
            val tokenRef = BillerCatalog.generateTokenRef(paymentRail, docRef.id)
            val cleanBillerId = billerId.ifBlank { "direct" }.take(128)

            val payload = mapOf(
                "userId" to uid,
                "billerId" to cleanBillerId,
                "category" to category.code,
                "providerName" to providerName.trim().take(80),
                "maskedIdentifier" to maskedIdentifier.trim().take(32),
                "amountPaise" to amountPaise,
                "convenienceFeePaise" to paymentRail.feePaise,
                "paymentRail" to paymentRail.code,
                "tokenRef" to tokenRef,
                "bbpsRefId" to bbpsRefId,
                "status" to initialStatus.code,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()

            // Also refresh linked biller's latest bill amount via BillerRepository
            if (cleanBillerId != "direct") {
                billerRepository.refreshBillAmount(cleanBillerId, amountPaise)
            }

            Result.success(
                BillTransaction(
                    id = docRef.id,
                    userId = uid,
                    billerId = cleanBillerId,
                    category = category.code,
                    providerName = providerName,
                    maskedIdentifier = maskedIdentifier,
                    amountPaise = amountPaise,
                    convenienceFeePaise = paymentRail.feePaise,
                    paymentRail = paymentRail.code,
                    tokenRef = tokenRef,
                    bbpsRefId = bbpsRefId,
                    status = initialStatus.code
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTransactionStatus(
        transactionId: String,
        newStatus: TransactionStatus
    ): Result<Unit> {
        var docPath = "users/unauthenticated/transactions/$transactionId"
        return try {
            val uid = requireUserId()
            require(transactionId.isNotBlank()) { "Transaction ID is required." }
            val docRef = transactionsCol(uid).document(transactionId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "status" to newStatus.code,
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

    // --- GRIEVANCE / DISPUTE MUTATIONS ---

    suspend fun raiseGrievance(
        transaction: BillTransaction,
        reason: GrievanceReason,
        notes: String
    ): Result<String> {
        var docPath = "users/unauthenticated/grievances"
        return try {
            val uid = requireUserId()
            require(transaction.id.isNotBlank()) { "Valid transaction is required." }
            val docRef = grievancesCol(uid).document()
            docPath = docRef.path
            val complaintRef = BillerCatalog.generateComplaintRef(transaction.bbpsRefId)
            val cleanNotes = notes.trim().take(400)

            val payload = mapOf(
                "userId" to uid,
                "transactionId" to transaction.id,
                "bbpsRefId" to transaction.bbpsRefId,
                "complaintRef" to complaintRef,
                "reason" to reason.code,
                "notes" to cleanNotes,
                "slaWindow" to "T+1 to T+5 Business Days",
                "status" to GrievanceStatus.OPEN.code,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(complaintRef)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateGrievanceStatus(
        ticketId: String,
        newStatus: GrievanceStatus,
        notes: String
    ): Result<Unit> {
        var docPath = "users/unauthenticated/grievances/$ticketId"
        return try {
            val uid = requireUserId()
            require(ticketId.isNotBlank()) { "Ticket ID is required." }
            val docRef = grievancesCol(uid).document(ticketId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "status" to newStatus.code,
                    "notes" to notes.trim().take(400),
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
}
