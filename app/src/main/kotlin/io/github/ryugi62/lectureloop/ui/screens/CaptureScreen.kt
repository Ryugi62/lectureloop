package io.github.ryugi62.lectureloop.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ryugi62.lectureloop.R
import io.github.ryugi62.lectureloop.adapters.audio.RecorderState
import io.github.ryugi62.lectureloop.application.ProcessPhase
import io.github.ryugi62.lectureloop.domain.AudioRef
import io.github.ryugi62.lectureloop.domain.Timestamp
import io.github.ryugi62.lectureloop.ui.BottomCta
import io.github.ryugi62.lectureloop.ui.Card
import io.github.ryugi62.lectureloop.ui.CaptureStep
import io.github.ryugi62.lectureloop.ui.IconButtonPlain
import io.github.ryugi62.lectureloop.ui.Skeleton
import io.github.ryugi62.lectureloop.ui.theme.Loop
import io.github.ryugi62.lectureloop.ui.theme.LoopType
import kotlinx.coroutines.delay

@Composable
fun CaptureTopBar(onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
        IconButtonPlain(R.drawable.ic_close, "Close", onClose)
    }
}

@Composable
fun RecordStep(recorder: RecorderState, onStart: () -> Unit, onStop: () -> Unit, onImport: () -> Unit, onClose: () -> Unit) {
    val recording = recorder as? RecorderState.Recording
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(recording != null) {
        while (recording != null) { now = SystemClock.elapsedRealtime(); delay(250) }
    }
    Column(Modifier.fillMaxSize()) {
        CaptureTopBar(onClose)
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Loop.side), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Text(
                if (recording != null) "Recording your lecture" else "Put your phone on the desk\nand press record",
                style = LoopType.title, color = Loop.colors.text, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(48.dp))
            val seconds = recording?.let { ((now - it.startedAtElapsed) / 1000).toInt().coerceAtLeast(0) } ?: 0
            Text(Timestamp(seconds).label, style = LoopType.number.copy(fontSize = LoopType.number.fontSize * 1.4f), color = Loop.colors.text)
            Spacer(Modifier.height(24.dp))
            LevelBar(recording?.level ?: 0f)
            Spacer(Modifier.height(32.dp))
            Box(Modifier.size(88.dp).clip(CircleShape).background(if (recording != null) Loop.colors.error else Loop.colors.blue), contentAlignment = Alignment.Center) {
                Icon(painterResource(if (recording != null) R.drawable.ic_stop else R.drawable.ic_mic), null, Modifier.size(36.dp), tint = Color.White)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Keeps recording with the screen off. When you stop, the audio is sent to Google Gemini once to build your card. Record only where your school allows it.",
                style = LoopType.caption, color = Loop.colors.caption, textAlign = TextAlign.Center,
            )
            if (recorder is RecorderState.Error) {
                Spacer(Modifier.height(12.dp))
                Text(recorder.message, style = LoopType.body, color = Loop.colors.error)
            }
        }
        BottomCta(text = if (recording != null) "Stop and build my card" else "Start recording", onClick = if (recording != null) onStop else onStart) {
            if (recording == null) TextButton(onClick = onImport) { Text("Import a recording instead", style = LoopType.bodyStrong, color = Loop.colors.blue) }
        }
    }
}

@Composable
private fun LevelBar(level: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(40.dp)) {
        val weights = listOf(0.35f, 0.6f, 0.85f, 1f, 0.85f, 0.6f, 0.35f)
        weights.forEach { w ->
            Box(Modifier.width(6.dp).height((6 + 34 * level * w).dp).clip(RoundedCornerShape(3.dp)).background(Loop.colors.blue.copy(alpha = 0.3f + 0.7f * level)))
        }
    }
}

@Composable
fun NameClassStep(audio: AudioRef, initial: String, onContinue: (String) -> Unit, onClose: () -> Unit) {
    var course by remember { mutableStateOf(initial) }
    Column(Modifier.fillMaxSize().imePadding()) {
        CaptureTopBar(onClose)
        Column(Modifier.weight(1f).padding(horizontal = Loop.side)) {
            Spacer(Modifier.height(16.dp))
            Text("Which class was this?", style = LoopType.title, color = Loop.colors.text)
            Spacer(Modifier.height(8.dp))
            Text("${Timestamp(audio.durationSeconds).label} recording · cards are grouped by class", style = LoopType.body, color = Loop.colors.textSub)
            Spacer(Modifier.height(Loop.gap))
            OutlinedTextField(
                value = course,
                onValueChange = { course = it.take(40) },
                placeholder = { Text("e.g. Signals & Systems", style = LoopType.body) },
                singleLine = true,
                textStyle = LoopType.body,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Loop.colors.blue),
                modifier = Modifier.fillMaxWidth().testTag("course"),
            )
        }
        BottomCta(text = "Build my review card", onClick = { onContinue(course) }, enabled = course.isNotBlank())
    }
}

@Composable
fun ProcessingStep(step: CaptureStep.Processing) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = Loop.side).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(40.dp))
        Text("${step.elapsed}s", style = LoopType.number, color = Loop.colors.blue)
        Spacer(Modifier.height(4.dp))
        Text("Building your review card", style = LoopType.title, color = Loop.colors.text)
        Spacer(Modifier.height(Loop.gap))
        val listening = step.phase as? ProcessPhase.Listening
        PhaseRow("Checking this week's allowance", done = step.phase != ProcessPhase.CheckingAllowance, active = step.phase == ProcessPhase.CheckingAllowance)
        PhaseRow(
            when {
                (listening?.attempt ?: 1) > 1 -> "Listening again with fixes (try 2)"
                (listening?.parts ?: 1) > 1 -> "Listening to ${Timestamp(step.audio.durationSeconds).label} of lecture in ${listening!!.parts} parts"
                else -> "Listening to ${Timestamp(step.audio.durationSeconds).label} of lecture"
            },
            done = step.phase == ProcessPhase.CheckingCard || step.phase == ProcessPhase.Saving,
            active = listening != null,
        )
        PhaseRow("Checking every timestamp and the 5 questions", done = step.phase == ProcessPhase.Saving, active = step.phase == ProcessPhase.CheckingCard)
        PhaseRow("Adding it to your day 1 · 3 · 7 loop", done = false, active = step.phase == ProcessPhase.Saving)
        Spacer(Modifier.height(Loop.gap))
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Skeleton(Modifier.fillMaxWidth(0.5f).height(14.dp))
                Skeleton(Modifier.fillMaxWidth().height(20.dp))
                Skeleton(Modifier.fillMaxWidth(0.8f).height(20.dp))
                Skeleton(Modifier.fillMaxWidth(0.3f).height(24.dp))
            }
        }
    }
}

@Composable
private fun PhaseRow(text: String, done: Boolean, active: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(
                when {
                    done -> Loop.colors.ok
                    active -> Loop.colors.blue
                    else -> Loop.colors.surface
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(16.dp), tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = if (active) LoopType.bodyStrong else LoopType.body, color = if (done || active) Loop.colors.text else Loop.colors.caption)
    }
}

@Composable
fun FailedStep(title: String, message: String, cta: String, onRetry: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        CaptureTopBar(onClose)
        Column(Modifier.weight(1f).padding(horizontal = Loop.side)) {
            Spacer(Modifier.height(16.dp))
            Text(title, style = LoopType.title, color = Loop.colors.text)
            Spacer(Modifier.height(12.dp))
            Text(message, style = LoopType.body, color = Loop.colors.textSub)
        }
        BottomCta(text = cta, onClick = onRetry)
    }
}
