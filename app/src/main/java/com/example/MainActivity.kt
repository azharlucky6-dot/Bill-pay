package com.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.BillRailRepository
import com.example.data.BillerRepository
import com.example.security.DeviceSecurityInspector
import com.example.ui.BillRailViewModel
import com.example.ui.UiState
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.LedgerScreen
import com.example.ui.screens.LegalScreen
import com.example.ui.screens.PayDashboardScreen
import com.example.ui.screens.SecurityScreen
import com.example.ui.theme.BillRailTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_PAY = "pay"
const val ROUTE_LEDGER = "ledger"
const val ROUTE_SECURITY = "security"
const val ROUTE_LEGAL = "legal"

private data class RailDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val railDestinations = listOf(
    RailDestination(
        route = ROUTE_PAY,
        labelRes = R.string.nav_pay,
        selectedIcon = Icons.Filled.AccountBalanceWallet,
        unselectedIcon = Icons.Outlined.AccountBalanceWallet,
        testTag = "nav_tab_pay"
    ),
    RailDestination(
        route = ROUTE_LEDGER,
        labelRes = R.string.nav_history,
        selectedIcon = Icons.Filled.ReceiptLong,
        unselectedIcon = Icons.Outlined.ReceiptLong,
        testTag = "nav_tab_ledger"
    ),
    RailDestination(
        route = ROUTE_SECURITY,
        labelRes = R.string.nav_security,
        selectedIcon = Icons.Filled.Shield,
        unselectedIcon = Icons.Outlined.Shield,
        testTag = "nav_tab_security"
    ),
    RailDestination(
        route = ROUTE_LEGAL,
        labelRes = R.string.nav_legal,
        selectedIcon = Icons.Filled.Gavel,
        unselectedIcon = Icons.Outlined.Gavel,
        testTag = "nav_tab_legal"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BillRailTheme {
                BillRailAppRoot()
            }
        }
    }
}

@Composable
fun BillRailAppRoot(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        // Clear FLAG_SECURE on unauthenticated screen only if desired, or keep safe default
        AuthScreen(onAuthSuccess = { /* AuthStateListener updates currentUser reactively */ })
    } else {
        // Key composition & ViewModel strictly by UID so sign-out/account switch disposes listeners cleanly
        key(user.uid) {
            BillRailAuthenticatedShell(
                currentUserId = user.uid,
                userDisplayName = user.displayName ?: "BillRail User",
                userEmail = user.email ?: ""
            )
        }
    }
}

@Composable
private fun BillRailAuthenticatedShell(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: BillRailViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val billerRepo = BillerRepository(db)
                BillRailViewModel(
                    repository = BillRailRepository(db = db, billerRepository = billerRepo),
                    billerRepository = billerRepo,
                    currentUserId = currentUserId,
                    userDisplayName = userDisplayName,
                    userEmail = userEmail
                )
            }
        }
    )

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val billersState by viewModel.billersState.collectAsStateWithLifecycle()
    val transactionsState by viewModel.transactionsState.collectAsStateWithLifecycle()
    val grievancesState by viewModel.grievancesState.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val lastReceipt by viewModel.lastReceipt.collectAsStateWithLifecycle()

    // Apply opt-in FLAG_SECURE window protection only when profile is loaded and flag changes
    val loadedScreenShield = (profileState as? UiState.Success)?.data?.screenShieldEnabled
    LaunchedEffect(activity, loadedScreenShield) {
        if (loadedScreenShield != null) {
            DeviceSecurityInspector.applyScreenShield(activity, loadedScreenShield)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.feedback.collect { feedback ->
            snackbarHostState.showSnackbar(
                message = feedback.message,
                duration = SnackbarDuration.Short
            )
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_PAY

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavigationRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    railDestinations.forEach { dest ->
                        val selected = currentRoute == dest.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateToTab(dest.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = stringResource(dest.labelRes)
                                )
                            },
                            label = { Text(stringResource(dest.labelRes)) },
                            modifier = Modifier.testTag(dest.testTag)
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            railDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTab(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_PAY
                    ) {
                        composable(ROUTE_PAY) {
                            PayDashboardScreen(
                                profileState = profileState,
                                billersState = billersState,
                                transactionsState = transactionsState,
                                isSubmitting = isSubmitting,
                                lastReceipt = lastReceipt,
                                onDismissReceipt = viewModel::dismissLastReceipt,
                                onNavigateToLegal = { navigateToTab(ROUTE_LEGAL) },
                                onSaveBiller = viewModel::saveBiller,
                                onUpdateBiller = viewModel::updateBiller,
                                onDeleteBiller = viewModel::deleteBiller,
                                onExecutePayment = viewModel::executeBillPayment
                            )
                        }
                        composable(ROUTE_LEDGER) {
                            LedgerScreen(
                                transactionsState = transactionsState,
                                grievancesState = grievancesState,
                                isSubmitting = isSubmitting,
                                onUpdateTransactionStatus = viewModel::updateTransactionStatus,
                                onRaiseGrievance = viewModel::raiseGrievance,
                                onUpdateGrievanceStatus = viewModel::updateGrievanceStatus
                            )
                        }
                        composable(ROUTE_SECURITY) {
                            SecurityScreen(
                                profileState = profileState,
                                billersState = billersState,
                                onUpdateSecurityPreferences = viewModel::updateSecurityPreferences
                            )
                        }
                        composable(ROUTE_LEGAL) {
                            LegalScreen(
                                profileState = profileState,
                                userEmail = userEmail,
                                isSubmitting = isSubmitting,
                                onAcceptTerms = viewModel::acceptTerms,
                                onSignOut = {
                                    signOutUser(
                                        credentialManager = credentialManager,
                                        scope = scope
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
