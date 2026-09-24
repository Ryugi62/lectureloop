package io.github.ryugi62.lectureloop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.ryugi62.lectureloop.R
import io.github.ryugi62.lectureloop.application.QuizResult
import io.github.ryugi62.lectureloop.domain.Lecture
import io.github.ryugi62.lectureloop.domain.Timestamp
import io.github.ryugi62.lectureloop.ui.BottomCta
import io.github.ryugi62.lectureloop.ui.Card
import io.github.ryugi62.lectureloop.ui.IconButtonPlain
import io.github.ryugi62.lectureloop.ui.TimeChip
import io.github.ryugi62.lectureloop.ui.animatedProgress
import io.github.ryugi62.lectureloop.ui.theme.Loop
import io.github.ryugi62.lectureloop.ui.theme.LoopType
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One question per screen (SPEC §8 item 2). */
@Composable
fun QuizScreen(
    lecture: Lecture,
    playingAt: Timestamp?,
    result: QuizResult?,
    onPlay: (Timestamp) -> Unit,
    onSubmit: (List<Int?>) -> Unit,
    onClose: () -> Unit,
) {
    if (result != null) return QuizResultView(result, onClose)
    val quiz = lecture.card.quiz
    var index by remember { mutableIntStateOf(0) }
    val answers = remember { mutableStateListOf<Int?>().apply { repeat(quiz.size) { add(null) } } }
    val item = quiz[index]
    val picked = answers[index]

    val progress = animatedProgress((index + if (picked != null) 1 else 0) / quiz.size.toFloat())
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButtonPlain(R.drawable.ic_close, "Close quiz", onClose)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = Loop.colors.blue, trackColor = Loop.colors.surface,
                gapSize = 0.dp, drawStopIndicator = {},
            )
            Spacer(Modifier.width(12.dp))
            Text("${index + 1}/${quiz.size}", style = LoopType.bodyStrong, color = Loop.colors.textSub)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Loop.side)) {
            Spacer(Modifier.height(16.dp))
            Text(item.question, style = LoopType.title, color = Loop.colors.text)
            Spacer(Modifier.height(Loop.gap))
            item.choices.forEachIndexed { i, choice ->
                val state = when {
                    picked == null -> ChoiceState.Idle
                    i == item.answerIndex -> ChoiceState.Right
                    i == picked -> ChoiceState.Wrong
                    else -> ChoiceState.Dim
                }
                Choice(choice, state, "choice-$i") { if (picked == null) answers[index] = i }
                Spacer(Modifier.height(10.dp))
            }
            if (picked != null) {
                Spacer(Modifier.height(12.dp))
                Card {
                    Column {
                        Text(if (picked == item.answerIndex) "Right." else "Not quite.", style = LoopType.bodyStrong, color = if (picked == item.answerIndex) Loop.colors.ok else Loop.colors.error)
                        Spacer(Modifier.height(6.dp))
                        Text(item.explanation, style = LoopType.body, color = Loop.colors.textSub)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Hear it in the lecture", style = LoopType.caption, color = Loop.colors.caption)
                            Spacer(Modifier.width(8.dp))
                            TimeChip(item.at, playingAt == item.at) { onPlay(item.at) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        BottomCta(
            text = if (index == quiz.lastIndex) "See my score" else "Next question",
            enabled = picked != null,
            onClick = { if (index == quiz.lastIndex) onSubmit(answers.toList()) else index++ },
        )
    }
}

private enum class ChoiceState { Idle, Right, Wrong, Dim }

@Composable
private fun Choice(text: String, state: ChoiceState, tag: String, onClick: () -> Unit) {
    val (bg, border, fg) = when (state) {
        ChoiceState.Idle -> Triple(Loop.colors.surface, Loop.colors.surface, Loop.colors.text)
        ChoiceState.Right -> Triple(Loop.colors.ok.copy(alpha = 0.12f), Loop.colors.ok, Loop.colors.text)
        ChoiceState.Wrong -> Triple(Loop.colors.error.copy(alpha = 0.10f), Loop.colors.error, Loop.colors.text)
        ChoiceState.Dim -> Triple(Loop.colors.surface, Loop.colors.surface, Loop.colors.caption)
    }
    Text(
        text,
        style = LoopType.body,
        color = fg,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg)
            .border(2.dp, border, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    )
}

@Composable
private fun QuizResultView(result: QuizResult, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).statusBarsPadding().padding(horizontal = Loop.side)) {
            Spacer(Modifier.height(48.dp))
            Text("${result.score}/${result.outOf}", style = LoopType.number.copy(fontSize = LoopType.number.fontSize * 1.5f), color = if (result.passed) Loop.colors.ok else Loop.colors.warn)
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    result.practice -> "Practice round. The real Day ${result.completedStep.dayOffset} quiz comes back later."
                    result.mastered -> "Mastered. This lecture leaves the loop."
                    result.passed -> "Day ${result.completedStep.dayOffset} done."
                    else -> "Almost. Let's try again tomorrow."
                },
                style = LoopType.title, color = Loop.colors.text,
            )
            Spacer(Modifier.height(Loop.gap))
            result.nextReviewAt?.let { next ->
                Card {
                    Column {
                        Text("Next review", style = LoopType.caption, color = Loop.colors.caption)
                        Spacer(Modifier.height(4.dp))
                        Text(next.atZone(ZoneId.systemDefault()).format(NEXT), style = LoopType.section, color = Loop.colors.text)
                        Spacer(Modifier.height(4.dp))
                        Text("Spacing reviews on day 1, 3 and 7 is what makes it stick.", style = LoopType.body, color = Loop.colors.textSub)
                    }
                }
            }
        }
        BottomCta(text = "Done", onClick = onClose)
    }
}

private val NEXT = DateTimeFormatter.ofPattern("EEEE, MMM d · h:mm a", Locale.US)
