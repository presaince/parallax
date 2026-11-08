/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.ui.component

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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

private val URL = Regex("""(https?://\S+|www\.\S+)""")
private val HASHTAG = Regex("""#[\p{L}\p{N}_]+""")

/**
 * Description text with live links. `SelectionContainer` around it lets the user select and copy,
 * which a plain `Text` cannot do, and the links open in the browser rather than doing nothing.
 */
@Composable
fun LinkifiedText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
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
        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        onTextLayout = { layout = it },
        modifier = modifier.pointerInput(annotated) {
            detectTapGestures { position ->
                val at = layout?.getOffsetForPosition(position) ?: return@detectTapGestures
                annotated.getStringAnnotations("URL", at, at).firstOrNull()?.let { annotation ->
                    val url = if (annotation.item.startsWith("http")) {
                        annotation.item
                    } else {
                        "https://${annotation.item}"
                    }
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
            }
        },
    )
}
