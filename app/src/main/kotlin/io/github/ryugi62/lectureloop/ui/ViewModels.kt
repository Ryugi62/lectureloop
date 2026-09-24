package io.github.ryugi62.lectureloop.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.ryugi62.lectureloop.application.FailureReason
import io.github.ryugi62.lectureloop.application.Offer
import io.github.ryugi62.lectureloop.application.ProStatus
import io.github.ryugi62.lectureloop.application.ProcessPhase
import io.github.ryugi62.lectureloop.application.ProcessResult
import io.github.ryugi62.lectureloop.application.PurchaseOutcome
import io.github.ryugi62.lectureloop.application.QuizResult
import io.github.ryugi62.lectureloop.domain.Access
import io.github.ryugi62.lectureloop.domain.AudioRef
import io.github.ryugi62.lectureloop.domain.Lecture
import io.github.ryugi62.lectureloop.domain.LectureId
import io.github.ryugi62.lectureloop.domain.PlanKind
import io.github.ryugi62.lectureloop.domain.PlanMath
import io.github.ryugi62.lectureloop.domain.Timestamp
import io.github.ryugi62.lectureloop.infrastructure.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class Factory(private val c: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = when (modelClass) {
        HomeViewModel::class.java -> HomeViewModel(c)
        CaptureViewModel::class.java -> CaptureViewModel(c)
        LectureViewModel::class.java -> LectureViewModel(c)
        PaywallViewModel::class.java -> PaywallViewModel(c, extras.createSavedStateHandle())
        AccountViewModel::class.java -> AccountViewModel(c)
        else -> error("Unknown ViewModel $modelClass")
    } as T
}

// ---------- Home ----------

data class HomeState(
    val loading: Boolean = true,
    val lectures: List<Lecture> = emptyList(),
    val due: List<Lecture> = emptyList(),
    val access: Access? = null,
)

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state

    init {
        viewModelScope.launch { c.lectures.version.collect { refresh() } }
        c.revenueCat?.let { rc -> viewModelScope.launch { rc.pro.collect { refresh() } } }
    }

    fun refresh() = viewModelScope.launch {
        val lectures = c.lectures.all()
        val due = c.todayQueue()
        val access = c.accessStatus()
        _state.value = HomeState(loading = false, lectures = lectures, due = due, access = access)
    }
}

// ---------- Capture: record / import → which class → processing ----------

sealed interface CaptureStep {
    data object Record : CaptureStep
    data object Importing : CaptureStep
    data class NameClass(val audio: AudioRef) : CaptureStep
    data class Processing(val audio: AudioRef, val course: String, val phase: ProcessPhase, val elapsed: Int) : CaptureStep
    data class Done(val lectureId: LectureId) : CaptureStep
    /** The recording is kept so it can be built right after an upgrade. */
    data class Paywalled(val access: Access.Paywalled, val audio: AudioRef, val course: String) : CaptureStep
    data class Failed(val message: String, val audio: AudioRef?, val course: String) : CaptureStep
}

class CaptureViewModel(private val c: AppContainer) : ViewModel() {
    private val _step = MutableStateFlow<CaptureStep>(CaptureStep.Record)
    val step: StateFlow<CaptureStep> = _step
    val lastCourse: String get() = c.prefs.getString("last_course", "") ?: ""

    /** One-shot guards: the screen re-enters composition when the paywall closes. */
    var autoStarted = false
    var handledStep: CaptureStep? = null
    private var ticker: Job? = null

    fun importFrom(uri: Uri) = viewModelScope.launch {
        _step.value = CaptureStep.Importing
        _step.value = try {
            CaptureStep.NameClass(c.importer.import(uri))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CaptureStep.Failed("We couldn't open that file. Try an m4a, mp3 or wav recording.", null, lastCourse)
        }
    }

    fun recorded(file: File) {
        _step.value = CaptureStep.NameClass(c.importer.describe(file))
    }

    fun process(audio: AudioRef, course: String) {
        c.prefs.edit().putString("last_course", course.trim()).apply()
        val started = System.currentTimeMillis()
        _step.value = CaptureStep.Processing(audio, course, ProcessPhase.CheckingAllowance, 0)
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _step.update { s -> if (s is CaptureStep.Processing) s.copy(elapsed = ((System.currentTimeMillis() - started) / 1000).toInt()) else s }
            }
        }
        viewModelScope.launch {
            val result = c.processLecture(course, audio) { phase ->
                _step.update { s -> if (s is CaptureStep.Processing) s.copy(phase = phase) else s }
            }
            ticker?.cancel()
            _step.value = when (result) {
                is ProcessResult.Done -> CaptureStep.Done(result.lecture.id)
                is ProcessResult.Paywalled -> CaptureStep.Paywalled(result.access, audio, course)
                is ProcessResult.Failed -> CaptureStep.Failed(result.reason.friendly(), audio, course)
            }
        }
    }

    fun reset() { _step.value = CaptureStep.Record }
}

internal fun FailureReason.friendly(): String = when (this) {
    is FailureReason.CardRejected -> "The card didn't pass our checks twice (${violations.first().message}). Your free lecture wasn't used — try again."
    FailureReason.Malformed -> "The AI answered in a broken format twice. Your free lecture wasn't used — try again."
    FailureReason.Busy -> "The AI service is busy right now. Wait a minute and try again — nothing was used."
    is FailureReason.Unreachable -> "We couldn't reach the AI service ($detail). Check your connection — nothing was used."
    is FailureReason.LimitReached -> "You've used your free lectures for this week."
}

// ---------- Lecture card + quiz ----------

data class LectureState(val lecture: Lecture? = null, val playingAt: Timestamp? = null)

class LectureViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(LectureState())
    val state: StateFlow<LectureState> = _state
    private val _result = MutableStateFlow<QuizResult?>(null)
    val result: StateFlow<QuizResult?> = _result

    fun load(id: String) = viewModelScope.launch {
        _state.update { it.copy(lecture = c.lectures.get(LectureId(id))) }
    }

    fun toggle(at: Timestamp) {
        val lecture = _state.value.lecture ?: return
        if (_state.value.playingAt == at && c.player.isPlaying) {
            c.player.pause()
            _state.update { it.copy(playingAt = null) }
        } else {
            runCatching { c.player.playFrom(lecture.audio, at.seconds) }
            _state.update { it.copy(playingAt = at) }
        }
    }

    fun stopAudio() {
        c.player.pause()
        _state.update { it.copy(playingAt = null) }
    }

    fun submit(answers: List<Int?>) = viewModelScope.launch {
        val lecture = _state.value.lecture ?: return@launch
        _result.value = c.submitQuiz(lecture.id, answers)
        load(lecture.id.value)
    }

    override fun onCleared() {
        c.player.release()
    }
}

// ---------- Paywall ----------

data class PaywallState(
    val loading: Boolean = true,
    val offer: Offer? = null,
    val selected: String? = null,
    val access: Access? = null,
    val purchasing: Boolean = false,
    val message: String? = null,
    val unlocked: Boolean = false,
)

class PaywallViewModel(private val c: AppContainer, args: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    /** The limit that sent us here — the server's count wins over the app's own when they differ. */
    private val arrivedWith: Access.Paywalled? = run {
        val used = args.get<String>("used")?.toIntOrNull()
        val limit = args.get<String>("limit")?.toIntOrNull()
        val resets = args.get<String>("resets")?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }
        if (used != null && limit != null && resets != null) Access.Paywalled(used, limit, resets) else null
    }
    private val _state = MutableStateFlow(PaywallState())
    val state: StateFlow<PaywallState> = _state
    val testStore: Boolean get() = c.billingConfigured && io.github.ryugi62.lectureloop.BuildConfig.DEBUG

    init { load() }

    fun load() = viewModelScope.launch {
        _state.update { it.copy(loading = true, message = null) }
        val access = arrivedWith ?: c.accessStatus()
        try {
            val offer = c.loadPaywall()
            val preselect = offer.plans.firstOrNull { it.kind == PlanKind.SEMESTER } ?: offer.plans.firstOrNull()
            _state.update { it.copy(loading = false, offer = offer, selected = preselect?.id, access = access) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, access = access, message = e.message ?: "Plans are unavailable right now.") }
        }
    }

    fun select(id: String) = _state.update { it.copy(selected = id) }

    fun buy() = viewModelScope.launch {
        val id = _state.value.selected ?: return@launch
        _state.update { it.copy(purchasing = true, message = null) }
        when (val outcome = c.purchase(id)) {
            PurchaseOutcome.Unlocked -> _state.update { it.copy(purchasing = false, unlocked = true) }
            PurchaseOutcome.Cancelled -> _state.update { it.copy(purchasing = false) }
            is PurchaseOutcome.Failed -> _state.update { it.copy(purchasing = false, message = outcome.message) }
        }
    }

    fun restore() = viewModelScope.launch {
        _state.update { it.copy(purchasing = true, message = null) }
        val status = runCatching { c.restore() }.getOrNull()
        _state.update {
            if (status?.isPro == true) it.copy(purchasing = false, unlocked = true)
            else it.copy(purchasing = false, message = "No active Semester Pass or monthly plan found for this account.")
        }
    }

    fun savingsText(): String? {
        val plans = _state.value.offer?.plans ?: return null
        val semester = plans.firstOrNull { it.kind == PlanKind.SEMESTER } ?: return null
        val monthly = plans.firstOrNull { it.kind == PlanKind.MONTHLY } ?: return null
        return PlanMath.savingsPercent(semester, monthly)?.let { "Save $it% vs monthly" }
    }
}

// ---------- Account ----------

data class AccountState(val loading: Boolean = true, val status: ProStatus? = null, val access: Access? = null, val message: String? = null)

class AccountViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state
    val billingConfigured get() = c.billingConfigured

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        val status = runCatching { c.billing.status() }.getOrNull()
        _state.value = AccountState(loading = false, status = status, access = c.accessStatus())
    }

    fun restore() = viewModelScope.launch {
        val status = runCatching { c.restore() }.getOrNull()
        _state.update { it.copy(status = status ?: it.status, message = if (status?.isPro == true) "Restored." else "Nothing to restore for this account.") }
        refresh()
    }
}
