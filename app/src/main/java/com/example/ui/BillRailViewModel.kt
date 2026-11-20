package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BillCategory
import com.example.data.BillRailRepository
import com.example.data.BillTransaction
import com.example.data.Biller
import com.example.data.BillerCatalog
import com.example.data.BillerProvider
import com.example.data.BillerRepository
import com.example.data.GrievanceReason
import com.example.data.GrievanceStatus
import com.example.data.GrievanceTicket
import com.example.data.PaymentRail
import com.example.data.TransactionStatus
import com.example.data.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class BillRailActionFeedback(
    val message: String,
    val isError: Boolean = false
)

class BillRailViewModel(
    private val repository: BillRailRepository,
    private val billerRepository: BillerRepository,
    val currentUserId: String,
    userDisplayName: String,
    userEmail: String
) : ViewModel() {

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _feedback = MutableSharedFlow<BillRailActionFeedback>(extraBufferCapacity = 4)
    val feedback: SharedFlow<BillRailActionFeedback> = _feedback.asSharedFlow()

    private val _lastReceipt = MutableStateFlow<BillTransaction?>(null)
    val lastReceipt: StateFlow<BillTransaction?> = _lastReceipt.asStateFlow()

    val profileState: StateFlow<UiState<UserProfile?>> = repository.observeProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            emit(handleStreamError("profile", error))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val billersState: StateFlow<UiState<List<Biller>>> = billerRepository.observeBillers(currentUserId)
        .map<List<Biller>, UiState<List<Biller>>> { UiState.Success(it) }
        .catch { error ->
            emit(handleStreamError("billers", error))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val transactionsState: StateFlow<UiState<List<BillTransaction>>> =
        repository.observeTransactions(currentUserId)
            .map<List<BillTransaction>, UiState<List<BillTransaction>>> { UiState.Success(it) }
            .catch { error ->
                emit(handleStreamError("transactions", error))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val grievancesState: StateFlow<UiState<List<GrievanceTicket>>> =
        repository.observeGrievances(currentUserId)
            .map<List<GrievanceTicket>, UiState<List<GrievanceTicket>>> { UiState.Success(it) }
            .catch { error ->
                emit(handleStreamError("grievances", error))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(userDisplayName, userEmail)
        }
    }

    private fun handleStreamError(streamName: String, error: Throwable): UiState.Error {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing $streamName", rootError)
        return UiState.Error(rootError.message ?: "Failed to load $streamName")
    }

    fun dismissLastReceipt() {
        _lastReceipt.value = null
    }

    fun saveBiller(
        category: BillCategory,
        provider: BillerProvider,
        nickname: String,
        rawIdentifier: String,
        amountRupeesText: String,
        dueDay: Int,
        autopayReminder: Boolean,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val amountRupees = amountRupeesText.trim().toDoubleOrNull() ?: 0.0
            val amountPaise = (amountRupees * 100).toLong().coerceAtLeast(0L)
            val result = billerRepository.addBiller(
                category = category,
                provider = provider,
                nickname = nickname,
                rawIdentifier = rawIdentifier,
                expectedAmountPaise = amountPaise,
                dueDayOfMonth = dueDay,
                autopayReminder = autopayReminder
            )
            _isSubmitting.value = false
            result.fold(
                onSuccess = {
                    _feedback.tryEmit(BillRailActionFeedback("Saved ${provider.providerName} to your BBPS billers"))
                    onSuccess()
                },
                onFailure = { err ->
                    _feedback.tryEmit(
                        BillRailActionFeedback(err.message ?: "Could not save biller", isError = true)
                    )
                }
            )
        }
    }

    fun updateBiller(
        billerId: String,
        nickname: String,
        amountRupeesText: String,
        dueDay: Int,
        autopayReminder: Boolean,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val amountRupees = amountRupeesText.trim().toDoubleOrNull() ?: 0.0
            val amountPaise = (amountRupees * 100).toLong().coerceAtLeast(0L)
            val result = billerRepository.updateBiller(
                billerId = billerId,
                nickname = nickname,
                lastBillAmountPaise = amountPaise,
                dueDayOfMonth = dueDay,
                autopayReminder = autopayReminder
            )
            _isSubmitting.value = false
            result.fold(
                onSuccess = {
                    _feedback.tryEmit(BillRailActionFeedback("Updated biller \"$nickname\""))
                    onSuccess()
                },
                onFailure = { err ->
                    _feedback.tryEmit(
                        BillRailActionFeedback(err.message ?: "Could not update biller", isError = true)
                    )
                }
            )
        }
    }

    fun deleteBiller(billerId: String, providerName: String) {
        viewModelScope.launch {
            val result = billerRepository.deleteBiller(billerId)
            result.fold(
                onSuccess = {
                    _feedback.tryEmit(BillRailActionFeedback("Removed $providerName"))
                },
                onFailure = { err ->
                    _feedback.tryEmit(
                        BillRailActionFeedback(err.message ?: "Failed to remove biller", isError = true)
                    )
                }
            )
        }
    }

    fun executeBillPayment(
        billerId: String,
        category: BillCategory,
        providerName: String,
        rawOrMaskedIdentifier: String,
        alreadyMasked: Boolean,
        amountRupeesText: String,
        paymentRail: PaymentRail,
        markPendingSettlement: Boolean,
        onSuccess: (BillTransaction) -> Unit
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val cleanIdentifier = if (alreadyMasked) {
                rawOrMaskedIdentifier.trim()
            } else {
                val sanitized = BillerCatalog.sanitizeIdentifier(category, rawOrMaskedIdentifier)
                if (sanitized.isFailure) {
                    _isSubmitting.value = false
                    _feedback.tryEmit(
                        BillRailActionFeedback(
                            sanitized.exceptionOrNull()?.message ?: "Invalid identifier",
                            isError = true
                        )
                    )
                    return@launch
                }
                sanitized.getOrThrow()
            }

            val rupees = amountRupeesText.trim().toDoubleOrNull()
            if (rupees == null || rupees < 1.0 || rupees > 1_000_000.0) {
                _isSubmitting.value = false
                _feedback.tryEmit(
                    BillRailActionFeedback("Enter a valid amount between ₹1 and ₹10,00,000", isError = true)
                )
                return@launch
            }

            val amountPaise = (rupees * 100).toLong()
            val status = if (markPendingSettlement) {
                TransactionStatus.PENDING_SETTLEMENT
            } else {
                TransactionStatus.SETTLED
            }

            val result = repository.recordBillPayment(
                billerId = billerId,
                category = category,
                providerName = providerName,
                maskedIdentifier = cleanIdentifier,
                amountPaise = amountPaise,
                paymentRail = paymentRail,
                initialStatus = status
            )
            _isSubmitting.value = false
            result.fold(
                onSuccess = { tx ->
                    _lastReceipt.value = tx
                    _feedback.tryEmit(
                        BillRailActionFeedback("BBPS Receipt ${tx.bbpsRefId} recorded via ${paymentRail.title}")
                    )
                    onSuccess(tx)
                },
                onFailure = { err ->
                    _feedback.tryEmit(
                        BillRailActionFeedback(err.message ?: "Payment recording failed", isError = true)
                    )
                }
            )
        }
    }

    fun updateTransactionStatus(transactionId: String, nextStatus: TransactionStatus) {
        viewModelScope.launch {
            val result = repository.updateTransactionStatus(transactionId, nextStatus)
            result.fold(
                onSuccess = {
                    _feedback.tryEmit(BillRailActionFeedback("Settlement status updated to ${nextStatus.label}"))
                },
                onFailure = { err ->
                    _feedback.tryEmit(
                        BillRailActionFeedback(err.message ?: "Status transition rejected", isError = true)
                    )
                }
            )
        }
    }

    fun raiseGrievance(
        transaction: BillTransaction,
        reason: GrievanceReason,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.raiseGrievance(transaction, reason, notes)
            _isSubmitting.value = false
            result.fold(
                onSuccess = { crn ->
                    _feedback.tryEmit(
                        BillRailActionFeedback("BBPS Complaint $crn logged (T+1 to T+5 SLA)")
                    )
                    onSuccess()
                },
                onFailure = { err ->
                    _feedback.tryEmit(
                        BillRailActionFeedback(err.message ?: "Could not raise complaint", isError = true)
                    )
                }
            )
        }
    }

    fun updateGrievanceStatus(ticketId: String, nextStatus: GrievanceStatus, notes: String) {
        viewModelScope.launch {
            val result = repository.updateGrievanceStatus(ticketId, nextStatus, notes)
            result.fold(
                onSuccess = {
                    _feedback.tryEmit(BillRailActionFeedback("Dispute updated to ${nextStatus.label}"))
                },
                onFailure = { err ->
                    _feedback.tryEmit(
                        BillRailActionFeedback(err.message ?: "Could not update ticket", isError = true)
                    )
                }
            )
        }
    }

    fun acceptTerms() {
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.acceptTerms(BillerCatalog.CURRENT_TERMS_VERSION)
            _isSubmitting.value = false
            result.fold(
                onSuccess = {
                    _feedback.tryEmit(
                        BillRailActionFeedback("Terms ${BillerCatalog.CURRENT_TERMS_VERSION} consent logged")
                    )
                },
                onFailure = { err ->
                    _feedback.tryEmit(
                        BillRailActionFeedback(err.message ?: "Failed to record consent", isError = true)
                    )
                }
            )
        }
    }

    fun updateSecurityPreferences(screenShieldEnabled: Boolean, biometricConfirmEnabled: Boolean) {
        viewModelScope.launch {
            val result = repository.updateSecurityPreferences(
                screenShieldEnabled = screenShieldEnabled,
                biometricConfirmEnabled = biometricConfirmEnabled
            )
            result.fold(
                onSuccess = {
                    _feedback.tryEmit(BillRailActionFeedback("Security policy updated"))
                },
                onFailure = { err ->
                    _feedback.tryEmit(
                        BillRailActionFeedback(err.message ?: "Could not update security policy", isError = true)
                    )
                }
            )
        }
    }

    private companion object {
        const val TAG = "BillRailVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
