/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.ui.component

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.presaince.glasstube.ui.theme.accentTextColour

private val URL = Regex("""(https?://\S+|www\.\S+)""")
private val HASHTAG = Regex("""#[\p{L}\p{N}_]+""")

/** The spans that read as links, for the one caller that has to know what a tap would open. */
internal fun linkRanges(text: String): List<IntRange> = URL.findAll(text).map { it.range }.toList()

/** `www.` alone is not a URL: the platform needs the scheme before it can open it. */
internal fun absoluteUrl(raw: String): String = if (raw.startsWith("http")) raw else "https://$raw"

/**
 * Text with live links, for a body the reader can also tap to open or close something else: a tap
 * that lands on a link opens it, and one that does not is handed back to [onTapElsewhere].
 * `SelectionContainer` around it still lets the reader select and copy.
 */
@Composable
fun LinkifiedText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
    colour: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onTapElsewhere: (() -> Unit)? = null,
    onOverflow: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val linkColor = accentTextColour()
    val annotated = remember(text, linkColor) {
        val builder = AnnotatedString.Builder(text)
        URL.findAll(text).forEach { match ->
            builder.addStyle(
                SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Medium,
                ),
                match.range.first,
                match.range.last + 1,
            )
            builder.addStringAnnotation("URL", match.value, match.range.first, match.range.last + 1)
        }
        // Hashtags are the other thing YouTube colours in a description.
        HASHTAG.findAll(text).forEach { match ->
            builder.addStyle(
                SpanStyle(color = linkColor, fontWeight = FontWeight.Medium),
                match.range.first,
                match.range.last + 1,
            )
        }
        builder.toAnnotatedString()
    }

    var layout: TextLayoutResult? by remember { mutableStateOf(null) }

    Text(
        text = annotated,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = style,
        color = colour,
        onTextLayout = {
            layout = it
            onOverflow?.invoke(it.hasVisualOverflow)
        },
        modifier = modifier.pointerInput(annotated) {
            detectTapGestures { position ->
                val laidOut = layout
                val offset = laidOut?.getOffsetForPosition(position)
                val url = offset?.let { at ->
                    annotated.getStringAnnotations("URL", at, at).firstOrNull()
                }
                if (url == null) {
                    onTapElsewhere?.invoke()
                    return@detectTapGestures
                }
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(absoluteUrl(url.item))))
                }
            }
        },
    )
}
