package io.github.ryugi62.lectureloop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ryugi62.lectureloop.R
import io.github.ryugi62.lectureloop.domain.Access
import io.github.ryugi62.lectureloop.domain.PlanKind
import io.github.ryugi62.lectureloop.domain.PlanMath
import io.github.ryugi62.lectureloop.domain.PlanOption
import io.github.ryugi62.lectureloop.ui.BottomCta
import io.github.ryugi62.lectureloop.ui.IconButtonPlain
import io.github.ryugi62.lectureloop.ui.PaywallState
import io.github.ryugi62.lectureloop.ui.Pill
import io.github.ryugi62.lectureloop.ui.Skeleton
import io.github.ryugi62.lectureloop.ui.theme.Loop
import io.github.ryugi62.lectureloop.ui.theme.LoopType
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PaywallScreen(
    state: PaywallState,
    savings: String?,
    testStore: Boolean,
    onSelect: (String) -> Unit,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
            IconButtonPlain(R.drawable.ic_close, "Close", onClose)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Loop.side)) {
            val access = state.access
            if (access is Access.Paywalled) {
                Text("${access.used}/${access.limit}", style = LoopType.number, color = Loop.colors.blue)
                Spacer(Modifier.height(4.dp))
                Text(
                    "free lectures used this week · resets ${access.resetsAt.atZone(ZoneId.systemDefault()).format(DAY)}",
                    style = LoopType.body, color = Loop.colors.textSub,
                )
                Spacer(Modifier.height(16.dp))
            }
            Text(state.offer?.headline ?: "Keep every lecture in the loop", style = LoopType.title, color = Loop.colors.text)
            Spacer(Modifier.height(16.dp))
            Benefit("Unlimited lectures, every week")
            Benefit("Every class gets its day 1 · 3 · 7 quiz")
            Benefit("Cancel anytime from Account")
            Spacer(Modifier.height(Loop.gap))

            when {
                state.loading -> repeat(2) { Skeleton(Modifier.fillMaxWidth().height(84.dp)); Spacer(Modifier.height(12.dp)) }
                state.offer == null -> Text(state.message ?: "Plans are unavailable right now.", style = LoopType.body, color = Loop.colors.error)
                else -> state.offer.plans.forEach { plan ->
                    PlanRow(plan, selected = plan.id == state.selected, badge = if (plan.kind == PlanKind.SEMESTER) savings else null) { onSelect(plan.id) }
                    Spacer(Modifier.height(12.dp))
                }
            }
            if (state.offer != null && state.message != null) {
                Text(state.message, style = LoopType.body, color = Loop.colors.error)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Subscriptions renew until cancelled." + if (testStore) " This build uses RevenueCat Test Store — no real payment is taken." else "",
                style = LoopType.caption, color = Loop.colors.caption, textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(16.dp))
        }
        val selected = state.offer?.plans?.firstOrNull { it.id == state.selected }
        BottomCta(
            text = when (selected?.kind) {
                PlanKind.SEMESTER -> "Start Semester Pass"
                null -> "Continue"
                else -> "Start ${selected.title}"
            },
            onClick = onBuy,
            enabled = selected != null,
            loading = state.purchasing,
        ) {
            TextButton(onClick = onRestore) { Text("Restore purchases", style = LoopType.bodyStrong, color = Loop.colors.blue) }
        }
    }
}

@Composable
private fun Benefit(text: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_check), null, Modifier.size(20.dp), tint = Loop.colors.ok)
        Spacer(Modifier.width(10.dp))
        Text(text, style = LoopType.body, color = Loop.colors.text)
    }
}

@Composable
private fun PlanRow(plan: PlanOption, selected: Boolean, badge: String?, onClick: () -> Unit) {
    val weekly = PlanMath.weeklyMicros(plan)?.let { PlanMath.formatMicros(it, plan.currency) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Loop.radius))
            .background(if (selected) Loop.colors.blueSoft else Loop.colors.surface)
            .border(2.dp, if (selected) Loop.colors.blue else Loop.colors.surface, RoundedCornerShape(Loop.radius))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .testTag("plan-${plan.kind.name.lowercase()}")
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (plan.kind) {
                    PlanKind.SEMESTER -> "Semester Pass"
                    PlanKind.MONTHLY -> "Monthly"
                    PlanKind.OTHER -> plan.title
                },
                style = LoopType.bodyStrong, color = Loop.colors.text, modifier = Modifier.weight(1f),
            )
            badge?.let { Pill(it) }
        }
        Text(
            plan.priceText + when (plan.periodMonths) {
                1 -> " / month"
                6 -> " / 6 months — one term plus the break"
                null -> ""
                else -> " / ${plan.periodMonths} months"
            },
            style = LoopType.body, color = Loop.colors.textSub,
        )
        weekly?.let { Text("$it a week", style = LoopType.caption, color = Loop.colors.caption) }
    }
}

private val DAY = DateTimeFormatter.ofPattern("EEEE", Locale.US)
