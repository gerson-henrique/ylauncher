package com.ykatchou.ylauncher.ui.theme

import androidx.compose.ui.text.font.FontFamily

/**
 * The brush face for the home proverb's 中文. Android ships no Kai/brush CJK font, so this falls
 * back to the serif CJK (Noto Serif CJK), which already reads as brush-adjacent — heavier, with
 * stroke contrast — much closer to ink than the default sans.
 *
 * TODO(tinta): bundle a real 楷体 (Kai / brush) CJK font, subset to only the glyphs the 99 curated
 * proverbs use (a few hundred characters), for a true calligraphic stroke without shipping a
 * multi-megabyte full CJK font.
 */
val ProverbBrush: FontFamily = FontFamily.Serif
