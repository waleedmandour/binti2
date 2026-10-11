// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour

package com.binti.dilink.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Binti brand colors — mirrors `app/src/main/res/values/colors.xml`.
 *
 * Primary palette:
 * - Binti Dark Blue  (#001C35) — backgrounds, dark mode surface
 * - Binti Light Blue (#0082C8) — primary actions, accents
 * - Binti Gold       (#D4AF37) — highlights, premium feel
 *
 * These colors are used by both the XML theme (via colors.xml) and the
 * Compose theme (via this file) so the visual identity is consistent
 * across both UI systems during the incremental Compose migration.
 *
 * @author Dr. Waleed Mandour
 */

// Brand colors
val BintiDarkBlue = Color(0xFF001C35)
val BintiLightBlue = Color(0xFF0082C8)
val BintiGold = Color(0xFFD4AF37)

// Light theme colors
val LightPrimary = BintiLightBlue
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFF42A5F5)
val LightOnPrimaryContainer = Color(0xFFFFFFFF)
val LightSecondary = BintiGold
val LightOnSecondary = Color(0xFF000000)
val LightBackground = Color(0xFFFAFAFA)
val LightOnBackground = Color(0xFF212121)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF212121)
val LightSurfaceVariant = Color(0xFFF5F5F5)
val LightOnSurfaceVariant = Color(0xFF757575)
val LightOutline = Color(0xFFBDBDBD)
val LightError = Color(0xFFF44336)
val LightOnError = Color(0xFFFFFFFF)
val LightSuccess = Color(0xFF4CAF50)
val LightWarning = Color(0xFFFF9800)

// Dark theme colors (Phase 4 — dark mode support)
val DarkPrimary = BintiLightBlue
val DarkOnPrimary = Color(0xFFFFFFFF)
val DarkPrimaryContainer = BintiDarkBlue
val DarkOnPrimaryContainer = BintiLightBlue
val DarkSecondary = BintiGold
val DarkOnSecondary = Color(0xFF000000)
val DarkBackground = BintiDarkBlue
val DarkOnBackground = Color(0xFFE0E0E0)
val DarkSurface = Color(0xFF0A2A4A)
val DarkOnSurface = Color(0xFFE0E0E0)
val DarkSurfaceVariant = Color(0xFF0D3556)
val DarkOnSurfaceVariant = Color(0xFFB0BEC5)
val DarkOutline = Color(0xFF546E7A)
val DarkError = Color(0xFFEF5350)
val DarkOnError = Color(0xFFFFFFFF)
val DarkSuccess = Color(0xFF66BB6A)
val DarkWarning = Color(0xFFFFB74D)

// Overlay (voice assistant overlay — always dark regardless of theme)
val OverlayBackground = Color(0xE6001C35) // 90% opaque dark blue
