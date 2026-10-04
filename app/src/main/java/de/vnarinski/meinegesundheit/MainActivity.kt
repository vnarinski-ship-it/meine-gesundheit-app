package de.vnarinski.meinegesundheit

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import de.vnarinski.meinegesundheit.data.SecuritySettingsRepository
import de.vnarinski.meinegesundheit.ui.screens.*
import de.vnarinski.meinegesundheit.ui.theme.MeineGesundheitTheme

class MainActivity : FragmentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val unlocked = mutableStateOf(false)
    val pendingOcrShare = mutableStateOf<Uri?>(null)
    private var authPromptShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        captureOcrShare(intent)
        val settings = SecuritySettingsRepository(this).load()
        unlocked.value = !settings.appLockEnabled
        setContent { MeineGesundheitTheme { App(this) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureOcrShare(intent)
    }

    private fun captureOcrShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "application/vnd.vnarinski.ocrhub+json") {
            pendingOcrShare.value = intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    override fun onStop() {
        super.onStop()
        val enabled = SecuritySettingsRepository(this).load().appLockEnabled
        if (enabled && !authPromptShowing) unlocked.value = false
    }

    fun requestUnlock(onUnavailable: (String) -> Unit = {}) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val manager = BiometricManager.from(this)
        val status = manager.canAuthenticate(authenticators)
        if (status != BiometricManager.BIOMETRIC_SUCCESS) {
            onUnavailable("Keine passende Biometrie oder Gerätesperre eingerichtet")
            return
        }
        authPromptShowing = true
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                authPromptShowing = false
                unlocked.value = true
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                authPromptShowing = false
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onUnavailable(errString.toString())
                }
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Meine Gesundheit entsperren")
            .setSubtitle("Fingerabdruck oder Gerätesperre verwenden")
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }
}

data class BottomItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun App(activity: MainActivity, vm: AppViewModel = viewModel()) {
    val security by vm.securitySettings.collectAsState()
    val unlocked by activity.unlocked
    val pendingOcrShare by activity.pendingOcrShare
    var lockError by remember { mutableStateOf<String?>(null) }

    if (security.appLockEnabled && !unlocked) {
        LaunchedEffect(security.appLockEnabled) { activity.requestUnlock { lockError = it } }
        Surface(Modifier) {
            LockScreen(
                error = lockError,
                onUnlock = { activity.requestUnlock { lockError = it } }
            )
        }
        return
    }

    val nav = rememberNavController()
    val items = listOf(
        BottomItem("home", "Start", Icons.Default.Home),
        BottomItem("health", "Gesundheit", Icons.Default.Favorite),
        BottomItem("food", "Essen", Icons.Default.AddCircle),
        BottomItem("timeline", "Verlauf", Icons.Default.DateRange),
        BottomItem("ai", "KI", Icons.Default.Star)
    )
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val message by vm.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }
    LaunchedEffect(Unit) { vm.checkBackupAgeAndNotify() }
    LaunchedEffect(pendingOcrShare) {
        pendingOcrShare?.let {
            vm.importOcrHubPayload(it)
            activity.pendingOcrShare.value = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                items.forEach { item ->
                    NavigationBarItem(
                        selected = route == item.route,
                        onClick = {
                            nav.navigate(item.route) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(vm, { nav.navigate("food") }, { nav.navigate("health") }, { nav.navigate("drive") }, { nav.navigate("ai") }, { nav.navigate("backup") }) }
            composable("health") { HealthScreen(nav) }
            composable("documents") { DocumentsScreen(vm) }
            composable("labs") { LabsScreen(vm) }
            composable("medications") { MedicationsScreen(vm) }
            composable("symptoms") { SymptomsScreen(vm) }
            composable("appointments") { DoctorAppointmentsScreen(vm) }
            composable("food") { FoodScreen(vm) }
            composable("timeline") { TimelineScreen(vm) }
            composable("ai") { AiScreen(vm) }
            composable("drive") { DriveScreen(vm) }
            composable("backup") { BackupScreen(vm) }
            composable("security") { SecurityScreen(vm) }
            composable("interop") { InteropScreen(vm) }
        }
    }
}
