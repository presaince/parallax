/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.ui.component

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.presaince.parallax.R

/**
 * A region's flag, from the flag-icons set, converted to a vector drawable.
 *
 * The first version drew bands from coloured rectangles. It read well until the flags with a
 * shape in them: Canada's leaf became a triangle, Korea's taegeuk became two discs, and the
 * United States lost its stars. These are the real paths now, generated once from
 * lipis/flag-icons (MIT) and stored as vectors, so there is still no bitmap to resample.
 *
 * FillBounds deliberately stretches the 4:3 flag to the row's shape. Cropping to fit would show
 * only the middle band of a horizontal tricolour, which is the one part that cannot identify it;
 * stretched and faint behind a scrim, every band and every emblem survives.
 */
@Composable
fun FlagBackground(
    code: String,
    modifier: Modifier = Modifier,
    alpha: Float = 0.26f,
) {
    val res = flagResource(code.uppercase()) ?: return
    Image(
        painter = painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        alpha = alpha,
        modifier = modifier,
    )
}

private fun flagResource(code: String): Int? = when (code) {
    "UA" -> R.drawable.flag_ua
    "GB" -> R.drawable.flag_gb
    "US" -> R.drawable.flag_us
    "CA" -> R.drawable.flag_ca
    "AU" -> R.drawable.flag_au
    "NZ" -> R.drawable.flag_nz
    "IE" -> R.drawable.flag_ie
    "DE" -> R.drawable.flag_de
    "FR" -> R.drawable.flag_fr
    "ES" -> R.drawable.flag_es
    "IT" -> R.drawable.flag_it
    "NL" -> R.drawable.flag_nl
    "BE" -> R.drawable.flag_be
    "PL" -> R.drawable.flag_pl
    "PT" -> R.drawable.flag_pt
    "SE" -> R.drawable.flag_se
    "NO" -> R.drawable.flag_no
    "DK" -> R.drawable.flag_dk
    "FI" -> R.drawable.flag_fi
    "CZ" -> R.drawable.flag_cz
    "SK" -> R.drawable.flag_sk
    "HU" -> R.drawable.flag_hu
    "RO" -> R.drawable.flag_ro
    "BG" -> R.drawable.flag_bg
    "GR" -> R.drawable.flag_gr
    "TR" -> R.drawable.flag_tr
    "IL" -> R.drawable.flag_il
    "IN" -> R.drawable.flag_in
    "JP" -> R.drawable.flag_jp
    "KR" -> R.drawable.flag_kr
    "BR" -> R.drawable.flag_br
    "MX" -> R.drawable.flag_mx
    "AR" -> R.drawable.flag_ar
    "ZA" -> R.drawable.flag_za
    "NG" -> R.drawable.flag_ng
    "EG" -> R.drawable.flag_eg
    "RU" -> R.drawable.flag_ru
    else -> null
}
