package io.github.shici.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shici.core.WordEntry

@Composable fun ScreenHeader(title: String, back: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
        else Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick, enabled = enabled, modifier = modifier.heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors()) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable fun WordHeading(entry: WordEntry, speak: (String) -> Unit, modifier: Modifier = Modifier, centered: Boolean = false) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Text(entry.word, fontSize = when { entry.word.length > 18 -> 30.sp; entry.word.length > 12 -> 40.sp; else -> 48.sp },
            fontWeight = FontWeight.Bold, lineHeight = 54.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (entry.phonetic.isNotBlank()) Text("/${entry.phonetic}/", color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false), fontSize = 17.sp)
            IconButton(onClick = { speak(entry.word) }) {
                Icon(Icons.AutoMirrored.Outlined.VolumeUp, "播放 ${entry.word} 的离线发音", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable fun QuietText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Original scalable landscape; no bitmap downloads, and no text baked into images. */
@Composable fun MountainWallpaper(dark: Boolean, modifier: Modifier = Modifier) {
    val sky = if (dark) listOf(Color(0xFF202732), Color(0xFF282732), Color(0xFF1A1D23))
        else listOf(Color(0xFFE2E5F1), Color(0xFFFFE4D4), Color(0xFFF3E5DF))
    Canvas(modifier.fillMaxSize().background(Brush.verticalGradient(sky))) {
        val heights = listOf(0.48f, 0.57f, 0.66f, 0.75f)
        heights.forEachIndexed { index, base ->
            val path = Path().apply {
                moveTo(0f, size.height * base)
                val offsets = listOf(0.04f, -0.015f, 0.025f, -0.065f, -0.025f, -0.09f, -0.06f, -0.04f)
                offsets.forEachIndexed { point, offset ->
                    lineTo(size.width * (point + 1) / 8, size.height * (base + offset * (1f - index * 0.08f)))
                }
                lineTo(size.width, size.height); lineTo(0f, size.height); close()
            }
            val color = if (dark) Color(0xFF111B25).copy(alpha = 0.25f + index * 0.1f)
                else listOf(Color(0xFFC8C9E0), Color(0xFFBFC3DA), Color(0xFFABB8D0), Color(0xFFD1CEDA))[index].copy(alpha = 0.58f)
            drawPath(path, color)
        }
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, if (dark) Color(0xFF17191D) else Color(0xFFF7ECE7)),
            startY = size.height * 0.6f))
    }
}

@Composable fun EmptyState(title: String, detail: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) AccentButton(action, onAction)
    }
}
