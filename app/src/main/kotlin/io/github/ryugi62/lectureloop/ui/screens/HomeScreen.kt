package io.github.ryugi62.lectureloop.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ryugi62.lectureloop.R
import io.github.ryugi62.lectureloop.domain.Access
import io.github.ryugi62.lectureloop.domain.Lecture
import io.github.ryugi62.lectureloop.ui.BottomCta
import io.github.ryugi62.lectureloop.ui.Card
import io.github.ryugi62.lectureloop.ui.HomeState
import io.github.ryugi62.lectureloop.ui.IconButtonPlain
import io.github.ryugi62.lectureloop.ui.LoopDots
import io.github.ryugi62.lectureloop.ui.Pill
import io.github.ryugi62.lectureloop.ui.Skeleton
import io.github.ryugi62.lectureloop.ui.theme.Loop
import io.github.ryugi62.lectureloop.ui.theme.LoopType
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    state: HomeState,
    onRecord: () -> Unit,
    onImport: () -> Unit,
    onOpen: (Lecture) -> Unit,
    onReview: (Lecture) -> Unit,
    onUpgrade: () -> Unit,
    onAccount: () -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = Loop.side, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("LectureLoop", style = LoopType.section, color = Loop.colors.blue, modifier = Modifier.weight(1f))
            IconButtonPlain(R.drawable.ic_person, "Account", onAccount)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Loop.side, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { AllowanceHeader(state.access, onUpgrade) }
            item { Spacer(Modifier.height(12.dp)) }
            item { Text("Today's loop", style = LoopType.section, color = Loop.colors.text) }
            if (state.loading) {
                item { Skeleton(Modifier.fillMaxWidth().height(92.dp)) }
            } else if (state.due.isEmpty()) {
                item {
                    Card {
                        Text(
                            if (state.lectures.isEmpty()) "Record your first lecture. Its quiz comes back tomorrow, then on day 3 and day 7."
                            else "Nothing due right now. Your next quiz will show up here.",
                            style = LoopType.body, color = Loop.colors.textSub,
                        )
                    }
                }
            } else {
                items(state.due, key = { "due-" + it.id.value }) { lecture -> DueCard(lecture, onReview) }
            }
            item { Spacer(Modifier.height(12.dp)) }
            if (state.lectures.isNotEmpty()) {
                item { Text("Your lectures", style = LoopType.section, color = Loop.colors.text) }
                items(state.lectures, key = { it.id.value }) { lecture -> LectureRow(lecture, onOpen) }
            }
        }
        BottomCta(text = "Record a lecture", onClick = onRecord) {
            TextButton(onClick = onImport, modifier = Modifier.testTag("import")) { Text("Import a recording", style = LoopType.bodyStrong, color = Loop.colors.blue) }
        }
    }
}

@Composable
private fun AllowanceHeader(access: Access?, onUpgrade: () -> Unit) {
    when (access) {
        null -> Skeleton(Modifier.fillMaxWidth().height(96.dp))
        is Access.Granted -> if (access.remainingFree == null) {
            Column {
                Pill("Semester Pass")
                Spacer(Modifier.height(8.dp))
                Text("Unlimited lectures", style = LoopType.title, color = Loop.colors.text)
            }
        } else {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${access.remainingFree}", style = LoopType.number, color = Loop.colors.text)
                    Spacer(Modifier.width(8.dp))
                    Text("free lectures left this week", style = LoopType.body, color = Loop.colors.textSub, modifier = Modifier.padding(bottom = 6.dp))
                }
                TextButton(onClick = onUpgrade, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text("Get unlimited with the Semester Pass", style = LoopType.bodyStrong, color = Loop.colors.blue)
                }
            }
        }
        is Access.Paywalled -> Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("0", style = LoopType.number, color = Loop.colors.text)
                Spacer(Modifier.width(8.dp))
                Text("free lectures left this week", style = LoopType.body, color = Loop.colors.textSub, modifier = Modifier.padding(bottom = 6.dp))
            }
            TextButton(onClick = onUpgrade, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("Resets ${access.resetsAt.atZone(ZoneId.systemDefault()).format(DAY)} · Unlock now", style = LoopType.bodyStrong, color = Loop.colors.blue)
            }
        }
    }
}

@Composable
private fun DueCard(lecture: Lecture, onReview: (Lecture) -> Unit) {
    Card(Modifier.testTag("due-card"), color = Loop.colors.blueSoft, onClick = { onReview(lecture) }) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(lecture.review.step?.let { "Day ${it.dayOffset}" } ?: "Review", color = androidx.compose.ui.graphics.Color.White, background = Loop.colors.blue)
                Spacer(Modifier.width(8.dp))
                Text(lecture.course, style = LoopType.caption, color = Loop.colors.textSub)
            }
            Spacer(Modifier.height(8.dp))
            Text(lecture.card.title, style = LoopType.bodyStrong, color = Loop.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text("5 questions · about 2 minutes", style = LoopType.caption, color = Loop.colors.textSub)
        }
    }
}

@Composable
private fun LectureRow(lecture: Lecture, onOpen: (Lecture) -> Unit) {
    Card(onClick = { onOpen(lecture) }) {
        Column {
            Text(lecture.course, style = LoopType.caption.copy(fontWeight = FontWeight.SemiBold), color = Loop.colors.blue)
            Spacer(Modifier.height(4.dp))
            Text(lecture.card.title, style = LoopType.bodyStrong, color = Loop.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LoopDots(done = lecture.review.step?.ordinal ?: 3, mastered = lecture.review.mastered)
                Spacer(Modifier.weight(1f))
                Text(lecture.recordedAt.atZone(ZoneId.systemDefault()).format(DATE), style = LoopType.caption, color = Loop.colors.caption)
            }
        }
    }
}

private val DAY = DateTimeFormatter.ofPattern("EEE", Locale.US)
private val DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US)
