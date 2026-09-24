package io.github.ryugi62.lectureloop.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ryugi62.lectureloop.BuildConfig
import io.github.ryugi62.lectureloop.R
import io.github.ryugi62.lectureloop.domain.Access
import io.github.ryugi62.lectureloop.ui.AccountState
import io.github.ryugi62.lectureloop.ui.BottomCta
import io.github.ryugi62.lectureloop.ui.Card
import io.github.ryugi62.lectureloop.ui.IconButtonPlain
import io.github.ryugi62.lectureloop.ui.Pill
import io.github.ryugi62.lectureloop.ui.Skeleton
import io.github.ryugi62.lectureloop.ui.theme.Loop
import io.github.ryugi62.lectureloop.ui.theme.LoopType
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AccountScreen(state: AccountState, billingConfigured: Boolean, onManage: () -> Unit, onUpgrade: () -> Unit, onRestore: () -> Unit, onBack: () -> Unit) {
    val pro = state.status?.isPro == true
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
            IconButtonPlain(R.drawable.ic_back, "Back", onBack)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Loop.side)) {
            Text("Account", style = LoopType.title, color = Loop.colors.text)
            Spacer(Modifier.height(Loop.gap))
            if (state.loading) Skeleton(Modifier.fillMaxWidth().height(96.dp)) else Card {
                Column {
                    Pill(if (pro) "Semester Pass · active" else "Free")
                    Spacer(Modifier.height(10.dp))
                    val access = state.access
                    Text(
                        when {
                            pro -> "Unlimited lectures"
                            access is Access.Granted -> "${access.remainingFree} free lectures left this week"
                            access is Access.Paywalled -> "0 free lectures left this week"
                            else -> "Free plan"
                        },
                        style = LoopType.section, color = Loop.colors.text,
                    )
                    state.status?.expiresAt?.let {
                        Spacer(Modifier.height(4.dp))
                        Text("Renews or ends ${it.atZone(ZoneId.systemDefault()).format(DATE)}", style = LoopType.body, color = Loop.colors.textSub)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (billingConfigured) {
                TextButton(onClick = onRestore) { Text("Restore purchases", style = LoopType.bodyStrong, color = Loop.colors.blue) }
            } else {
                Text("Billing isn't configured in this build. Add REVENUECAT_TEST_STORE_KEY to local.properties.", style = LoopType.body, color = Loop.colors.warn)
            }
            state.message?.let { Text(it, style = LoopType.body, color = Loop.colors.textSub) }
            Spacer(Modifier.height(Loop.gap))
            Text("Privacy", style = LoopType.section, color = Loop.colors.text)
            Spacer(Modifier.height(8.dp))
            Text(
                "Recordings and cards stay on this phone. When you build a card, that one recording is sent to Google Gemini to be analysed. Subscriptions are handled by RevenueCat with an anonymous ID.",
                style = LoopType.body, color = Loop.colors.textSub,
            )
            Spacer(Modifier.height(16.dp))
            Text("LectureLoop ${BuildConfig.VERSION_NAME} · open source (MIT)", style = LoopType.caption, color = Loop.colors.caption)
        }
        if (billingConfigured) {
            BottomCta(text = if (pro) "Manage subscription" else "See plans", onClick = if (pro) onManage else onUpgrade)
        }
    }
}

private val DATE = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US)
