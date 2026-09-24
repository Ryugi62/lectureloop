package io.github.ryugi62.lectureloop.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ryugi62.lectureloop.R
import io.github.ryugi62.lectureloop.domain.Timestamp
import io.github.ryugi62.lectureloop.ui.theme.Loop
import io.github.ryugi62.lectureloop.ui.theme.LoopType

/** The one primary action, full width, pinned to the bottom (SPEC §8 item 5). */
@Composable
fun BottomCta(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    secondary: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Loop.colors.background)
            .navigationBarsPadding()
            .padding(horizontal = Loop.side, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = Modifier.fillMaxWidth().height(Loop.cta).testTag("cta"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Loop.colors.blue, contentColor = Color.White),
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
            else Text(text, style = LoopType.bodyStrong.copy(fontSize = 17.sp))
        }
        if (secondary != null) {
            Spacer(Modifier.height(4.dp))
            secondary()
        }
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, color: Color = Loop.colors.surface, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val base = modifier.fillMaxWidth().clip(RoundedCornerShape(Loop.radius)).background(color)
    Box((if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base).padding(20.dp)) { content() }
}

/** "▶ 00:38" — plays the recording from the second this was said. */
@Composable
fun TimeChip(at: Timestamp, playing: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Loop.colors.blueSoft)
            .clickable(role = Role.Button, onClickLabel = "Play from ${at.label}", onClick = onClick)
            .testTag("chip-${at.label}")
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play), null, Modifier.size(14.dp), tint = Loop.colors.blue)
        Spacer(Modifier.width(6.dp))
        Text(at.label, style = LoopType.caption.copy(fontWeight = FontWeight.SemiBold), color = Loop.colors.blue)
    }
}

@Composable
fun Pill(text: String, color: Color = Loop.colors.blue, background: Color = Loop.colors.blueSoft) {
    Text(
        text,
        style = LoopType.caption.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(background).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun IconButtonPlain(@DrawableRes icon: Int, label: String, onClick: () -> Unit, tint: Color = Loop.colors.text) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClickLabel = label, onClick = onClick).testTag("icon-$label"),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), label, Modifier.size(24.dp), tint = tint) }
}

/** Grey placeholder block for skeleton loading (SPEC §8 item 10). */
@Composable
fun Skeleton(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "skeleton")
    val alpha by t.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha")
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Loop.colors.caption.copy(alpha = 0.18f * alpha)))
}

/** Day 1 · 3 · 7 dots; filled = done. */
@Composable
fun LoopDots(done: Int, mastered: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("D1", "D3", "D7").forEachIndexed { i, label ->
            val filled = mastered || i < done
            Text(
                label,
                style = LoopType.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                color = if (filled) Color.White else Loop.colors.caption,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (filled) Loop.colors.ok else Loop.colors.surface)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
fun animatedProgress(target: Float) = animateFloatAsState(target, tween(400), label = "progress").value
