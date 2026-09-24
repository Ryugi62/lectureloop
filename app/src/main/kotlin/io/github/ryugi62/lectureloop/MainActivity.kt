package io.github.ryugi62.lectureloop

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import android.provider.DocumentsContract
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.revenuecat.purchases.ui.revenuecatui.customercenter.CustomerCenter
import io.github.ryugi62.lectureloop.adapters.audio.RecorderState
import io.github.ryugi62.lectureloop.adapters.audio.RecordingService
import io.github.ryugi62.lectureloop.adapters.billing.CurrentActivity
import io.github.ryugi62.lectureloop.domain.Access
import io.github.ryugi62.lectureloop.ui.AccountViewModel
import io.github.ryugi62.lectureloop.ui.CaptureStep
import io.github.ryugi62.lectureloop.ui.CaptureViewModel
import io.github.ryugi62.lectureloop.ui.Factory
import io.github.ryugi62.lectureloop.ui.HomeViewModel
import io.github.ryugi62.lectureloop.ui.LectureViewModel
import io.github.ryugi62.lectureloop.ui.PaywallViewModel
import io.github.ryugi62.lectureloop.ui.screens.AccountScreen
import io.github.ryugi62.lectureloop.ui.screens.CardScreen
import io.github.ryugi62.lectureloop.ui.screens.FailedStep
import io.github.ryugi62.lectureloop.ui.screens.HomeScreen
import io.github.ryugi62.lectureloop.ui.screens.NameClassStep
import io.github.ryugi62.lectureloop.ui.screens.PaywallScreen
import io.github.ryugi62.lectureloop.ui.screens.ProcessingStep
import io.github.ryugi62.lectureloop.ui.screens.QuizScreen
import io.github.ryugi62.lectureloop.ui.screens.RecordStep
import io.github.ryugi62.lectureloop.ui.theme.Loop
import io.github.ryugi62.lectureloop.ui.theme.LectureLoopTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** A recording shared from another app ("Share → LectureLoop"). */
    private val sharedAudio = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        val factory = Factory((application as LectureLoopApp).container)
        setContent {
            LectureLoopTheme {
                Box(Modifier.fillMaxSize().background(Loop.colors.background).semantics { testTagsAsResourceId = true }) {
                    App(factory, sharedAudio)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { sharedAudio.value = it }
        }
    }

    override fun onResume() {
        super.onResume()
        CurrentActivity.set(this)
    }

    override fun onPause() {
        CurrentActivity.set(null)
        super.onPause()
    }
}

private const val UNLOCKED = "unlocked"

/** Opens the system file picker for audio, starting in Downloads where recorder apps and browsers save files. */
private class OpenAudio : androidx.activity.result.contract.ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: android.content.Context, input: Unit) =
        Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*")
            .putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildRootUri("com.android.providers.downloads.documents", "downloads"))

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = if (resultCode == android.app.Activity.RESULT_OK) intent?.data else null
}

private object Routes {
    const val HOME = "home"
    const val CAPTURE = "capture?import={import}"
    const val CARD = "card/{id}"
    const val QUIZ = "quiz/{id}"
    const val PAYWALL = "paywall?used={used}&limit={limit}&resets={resets}"
    fun paywall(access: Access.Paywalled? = null) =
        if (access == null) "paywall" else "paywall?used=${access.used}&limit=${access.limit}&resets=${access.resetsAt}"
    const val ACCOUNT = "account"
    fun capture(import: Boolean) = "capture?import=$import"
    fun card(id: String) = "card/$id"
    fun quiz(id: String) = "quiz/$id"
}

@Composable
private fun App(factory: Factory, sharedAudio: MutableStateFlow<Uri?>) {
    val nav = rememberNavController()
    val shared by sharedAudio.collectAsStateWithLifecycle()
    LaunchedEffect(shared) { if (shared != null) nav.navigate(Routes.capture(import = true)) }

    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val vm: HomeViewModel = viewModel(factory = factory)
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { vm.refresh() }
            HomeScreen(
                state = state,
                onRecord = { nav.navigate(Routes.capture(import = false)) },
                onImport = { nav.navigate(Routes.capture(import = true)) },
                onOpen = { nav.navigate(Routes.card(it.id.value)) },
                onReview = { nav.navigate(Routes.quiz(it.id.value)) },
                onUpgrade = { nav.navigate(Routes.paywall()) },
                onAccount = { nav.navigate(Routes.ACCOUNT) },
            )
        }
        composable(Routes.CAPTURE) { entry ->
            val wantsImport = entry.arguments?.getString("import") == "true"
            val unlocked by entry.savedStateHandle.getStateFlow(UNLOCKED, false).collectAsStateWithLifecycle()
            CaptureRoute(factory, nav, wantsImport, sharedAudio, unlocked) { entry.savedStateHandle[UNLOCKED] = false }
        }
        composable(Routes.CARD) { entry ->
            val id = entry.arguments?.getString("id")!!
            val vm: LectureViewModel = viewModel(factory = factory)
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(id) { vm.load(id) }
            state.lecture?.let { lecture ->
                CardScreen(lecture, state.playingAt, vm::toggle, onQuiz = { vm.stopAudio(); nav.navigate(Routes.quiz(id)) }, onBack = { nav.popBackStack() })
            }
        }
        composable(Routes.QUIZ) { entry ->
            val id = entry.arguments?.getString("id")!!
            val vm: LectureViewModel = viewModel(factory = factory)
            val state by vm.state.collectAsStateWithLifecycle()
            val result by vm.result.collectAsStateWithLifecycle()
            LaunchedEffect(id) { vm.load(id) }
            state.lecture?.let { lecture ->
                QuizScreen(lecture, state.playingAt, result, vm::toggle, vm::submit, onClose = { vm.stopAudio(); nav.popBackStack(Routes.HOME, inclusive = false) })
            }
        }
        composable(
            Routes.PAYWALL,
            arguments = listOf("used", "limit", "resets").map { name ->
                navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null }
            },
        ) {
            val vm: PaywallViewModel = viewModel(factory = factory)
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.unlocked) {
                if (state.unlocked) {
                    // Tell the capture flow (if we came from it) to build the waiting recording now.
                    nav.previousBackStackEntry?.savedStateHandle?.set(UNLOCKED, true)
                    nav.popBackStack()
                }
            }
            PaywallScreen(state, vm.savingsText(), vm.testStore, vm::select, vm::buy, vm::restore, onClose = { nav.popBackStack() })
        }
        composable(Routes.ACCOUNT) {
            val vm: AccountViewModel = viewModel(factory = factory)
            val state by vm.state.collectAsStateWithLifecycle()
            val showCenter = remember { mutableStateOf(false) }
            if (showCenter.value) {
                BackHandler { showCenter.value = false; vm.refresh() }
                CustomerCenter(modifier = Modifier.fillMaxSize(), onDismiss = { showCenter.value = false; vm.refresh() })
            } else {
                AccountScreen(state, vm.billingConfigured, onManage = { showCenter.value = true }, onUpgrade = { nav.navigate(Routes.paywall()) }, onRestore = vm::restore, onBack = { nav.popBackStack() })
            }
        }
    }
}

@Composable
private fun CaptureRoute(
    factory: Factory,
    nav: NavHostController,
    wantsImport: Boolean,
    sharedAudio: MutableStateFlow<Uri?>,
    unlocked: Boolean,
    consumeUnlocked: () -> Unit,
) {
    val vm: CaptureViewModel = viewModel(factory = factory)
    val step by vm.step.collectAsStateWithLifecycle()
    val recorder by RecordingService.stateFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val close: () -> Unit = { RecordingService.cancel(context); nav.popBackStack(Routes.HOME, inclusive = false) }

    val picker = rememberLauncherForActivityResult(OpenAudio()) { uri ->
        if (uri != null) vm.importFrom(uri) else if (step == CaptureStep.Record && wantsImport) close()
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.RECORD_AUDIO] == true) RecordingService.start(context)
    }
    LaunchedEffect(Unit) {
        if (vm.autoStarted) return@LaunchedEffect
        vm.autoStarted = true
        val shared = sharedAudio.value
        if (shared != null) { sharedAudio.value = null; vm.importFrom(shared) }
        else if (wantsImport) picker.launch(Unit)
    }
    LaunchedEffect(unlocked) {
        val s = step
        if (unlocked && s is CaptureStep.Paywalled) { consumeUnlocked(); vm.process(s.audio, s.course) }
    }
    LaunchedEffect(recorder) {
        val finished = recorder as? RecorderState.Finished ?: return@LaunchedEffect
        RecordingService.reset()
        vm.recorded(finished.file)
    }
    LaunchedEffect(step) {
        if (vm.handledStep === step) return@LaunchedEffect
        vm.handledStep = step
        when (val s = step) {
            is CaptureStep.Done -> nav.navigate(Routes.card(s.lectureId.value)) { popUpTo(Routes.HOME) }
            is CaptureStep.Paywalled -> nav.navigate(Routes.paywall(s.access))
            else -> Unit
        }
    }

    when (val s = step) {
        is CaptureStep.Paywalled -> FailedStep(
            title = "Your recording is waiting",
            message = "You've used ${s.access.used} of ${s.access.limit} free lectures this week. Unlock now and we'll build this card straight away — or close, and it resets on Monday.",
            cta = "See plans",
            onRetry = { nav.navigate(Routes.paywall(s.access)) },
            onClose = close,
        )
        CaptureStep.Record, CaptureStep.Importing, is CaptureStep.Done -> RecordStep(
            recorder = recorder,
            onStart = {
                val needed = buildList {
                    add(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    RecordingService.start(context)
                } else {
                    micPermission.launch(needed.toTypedArray())
                }
            },
            onStop = { RecordingService.stop(context) },
            onImport = { picker.launch(Unit) },
            onClose = close,
        )
        is CaptureStep.NameClass -> NameClassStep(s.audio, vm.lastCourse, onContinue = { vm.process(s.audio, it) }, onClose = close)
        is CaptureStep.Processing -> ProcessingStep(s)
        is CaptureStep.Failed -> FailedStep(
            title = "That didn't work",
            message = s.message,
            cta = if (s.audio != null) "Try again" else "Back",
            onRetry = { s.audio?.let { vm.process(it, s.course) } ?: close() },
            onClose = close,
        )
    }
}
