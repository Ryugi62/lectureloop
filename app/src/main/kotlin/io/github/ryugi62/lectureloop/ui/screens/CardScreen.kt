package io.github.ryugi62.lectureloop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.github.ryugi62.lectureloop.R
import io.github.ryugi62.lectureloop.domain.Lecture
import io.github.ryugi62.lectureloop.domain.Timestamp
import io.github.ryugi62.lectureloop.ui.BottomCta
import io.github.ryugi62.lectureloop.ui.Card
import io.github.ryugi62.lectureloop.ui.IconButtonPlain
import io.github.ryugi62.lectureloop.ui.LoopDots
import io.github.ryugi62.lectureloop.ui.Pill
import io.github.ryugi62.lectureloop.ui.TimeChip
import io.github.ryugi62.lectureloop.ui.theme.Loop
import io.github.ryugi62.lectureloop.ui.theme.LoopType

@Composable
fun CardScreen(lecture: Lecture, playingAt: Timestamp?, onPlay: (Timestamp) -> Unit, onQuiz: () -> Unit, onBack: () -> Unit) {
    val card = lecture.card
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButtonPlain(R.drawable.ic_back, "Back", onBack)
            Text(lecture.course, style = LoopType.bodyStrong, color = Loop.colors.textSub, modifier = Modifier.weight(1f))
            LoopDots(done = lecture.review.step?.ordinal ?: 3, mastered = lecture.review.mastered)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Loop.side)) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${card.examPoints.size}", style = LoopType.number, color = Loop.colors.blue)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (card.examPoints.size == 1) "thing your lecturer said will be tested" else "things your lecturer said will be tested",
                    style = LoopType.body, color = Loop.colors.textSub, modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(card.title, style = LoopType.title, color = Loop.colors.text)
            Spacer(Modifier.height(6.dp))
            Text(card.summary, style = LoopType.body, color = Loop.colors.textSub)

            Section("On the exam")
            if (card.examPoints.isEmpty()) {
                Card { Text("No exam hints in this lecture — we never invent them.", style = LoopType.body, color = Loop.colors.textSub) }
            }
            card.examPoints.forEach { p ->
                ExamPointCard(p.point, p.cue, p.at, playingAt == p.at) { onPlay(p.at) }
                Spacer(Modifier.height(12.dp))
            }

            Section("Key ideas")
            card.concepts.forEach { c ->
                Card {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.name, style = LoopType.bodyStrong, color = Loop.colors.text, modifier = Modifier.weight(1f))
                            TimeChip(c.at, playingAt == c.at) { onPlay(c.at) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(c.explanation, style = LoopType.body, color = Loop.colors.textSub)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (card.todos.isNotEmpty()) {
                Section("Before next class")
                card.todos.forEach { t ->
                    Card {
                        Column {
                            Text(t.task, style = LoopType.bodyStrong, color = Loop.colors.text)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                t.due?.let { Pill("Due: $it", color = Loop.colors.warn, background = Loop.colors.warn.copy(alpha = 0.12f)) }
                                Spacer(Modifier.weight(1f))
                                TimeChip(t.at, playingAt == t.at) { onPlay(t.at) }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        BottomCta(text = "Take the 5-question quiz", onClick = onQuiz)
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(Loop.gap))
    Text(title, style = LoopType.section, color = Loop.colors.text)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ExamPointCard(point: String, cue: String, at: Timestamp, playing: Boolean, onPlay: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Card(onClick = { open = !open }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(point, style = LoopType.bodyStrong, color = Loop.colors.text)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeChip(at, playing, onPlay)
                Spacer(Modifier.width(10.dp))
                Text(if (open) "Hide what they said" else "What they said", style = LoopType.caption, color = Loop.colors.caption)
            }
            AnimatedVisibility(open) {
                Text("“$cue”", style = LoopType.body.copy(fontStyle = FontStyle.Italic), color = Loop.colors.textSub)
            }
        }
    }
}
