package com.nextpage.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand-authored thin-stroke icon family for NextPage.
 *
 * Single source of truth for app iconography (REQ-I-01): every constant is a
 * stroke-only `ImageVector` on a uniform 24x24 viewport with 1.6f round-capped
 * strokes, so `Icon(tint = ...)` colors them uniformly — no hardcoded colors
 * (REQ-I-09/10). Constants are authored incrementally per PR; each keeps the
 * source Material/drawable name it replaced in its KDoc (REQ-I-07).
 */
object NextPageIcons {
    private const val IconStrokeWidth = 1.6f

    private fun nextPageIcon(
        name: String,
        autoMirror: Boolean = false,
        block: ImageVector.Builder.() -> ImageVector.Builder
    ): ImageVector {
        val builder = ImageVector.Builder(
            name = "NextPageIcons.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = autoMirror
        )
        block(builder)
        return builder.build()
    }

    private fun ImageVector.Builder.strokePath(block: PathBuilder.() -> Unit): ImageVector.Builder =
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = IconStrokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block
        )

    /**
     * Filled path for the rare glyphs that must read as solid (e.g. a rated
     * `Star`). Still `SolidColor(Color.Black)` so `Icon(tint = ...)` colors it
     * uniformly (REQ-I-09/10). Deliberate exception to the stroke-only rule.
     */
    private fun ImageVector.Builder.fillPath(block: PathBuilder.() -> Unit): ImageVector.Builder =
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            pathBuilder = block
        )

    /** Bottom nav "Home" tab. Source: `ic_nav_home`. */
    val Home = nextPageIcon("Home") {
        strokePath {
            moveTo(4f, 11f); lineTo(12f, 4f); lineTo(20f, 11f)
            moveTo(6f, 10f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 10f)
            moveTo(10f, 20f); lineTo(10f, 15f); lineTo(14f, 15f); lineTo(14f, 20f)
        }
    }

    /** Bottom nav "Library" tab — bookshelf with two rows of books. Source: `ic_nav_library`. */
    val Library = nextPageIcon("Library") {
        strokePath {
            moveTo(5f, 4f); lineTo(5f, 20f)
            moveTo(19f, 4f); lineTo(19f, 20f)
            moveTo(4f, 4f); lineTo(20f, 4f)
            moveTo(4f, 12f); lineTo(20f, 12f)
            moveTo(4f, 20f); lineTo(20f, 20f)
            moveTo(8f, 11f); lineTo(8f, 5f)
            moveTo(11f, 11f); lineTo(11f, 5f)
            moveTo(14f, 11f); lineTo(14f, 5f)
            moveTo(8f, 19f); lineTo(8f, 14f)
            moveTo(11f, 19f); lineTo(11f, 14f)
            moveTo(14f, 19f); lineTo(14f, 14f)
        }
    }

    /** Bottom nav "Highlights" tab — highlighter marker. Source: `ic_nav_highlights`. */
    val Highlights = nextPageIcon("Highlights") {
        strokePath {
            moveTo(8f, 4f); lineTo(18f, 14f); lineTo(14f, 18f); lineTo(4f, 8f); close()
            moveTo(4f, 8f); lineTo(1.8f, 5.8f)
        }
    }

    /** Bottom nav "Settings" tab — gear. Source: `ic_nav_settings`. */
    val Settings = nextPageIcon("Settings") {
        strokePath {
            moveTo(4.5f, 12f); arcTo(7.5f, 7.5f, 0f, true, true, 19.5f, 12f); arcTo(7.5f, 7.5f, 0f, true, true, 4.5f, 12f)
            moveTo(9.2f, 12f); arcTo(2.8f, 2.8f, 0f, true, true, 14.8f, 12f); arcTo(2.8f, 2.8f, 0f, true, true, 9.2f, 12f)
            moveTo(7.7f, 7.7f); lineTo(9.5f, 9.5f)
            moveTo(16.3f, 7.7f); lineTo(14.5f, 9.5f)
            moveTo(16.3f, 16.3f); lineTo(14.5f, 14.5f)
            moveTo(7.7f, 16.3f); lineTo(9.5f, 14.5f)
        }
    }

    /** Bottom nav "Statistics" tab — bar chart. Source: `ic_nav_statistics` (re-drawn in family, REQ-I-11). */
    val Statistics = nextPageIcon("Statistics") {
        strokePath {
            moveTo(4f, 20f); lineTo(20f, 20f)
            moveTo(7f, 20f); lineTo(7f, 12f)
            moveTo(12f, 20f); lineTo(12f, 7f)
            moveTo(17f, 20f); lineTo(17f, 15f)
        }
    }

    /** Reader route icon. Source: `ic_nav_home` placeholder — dedicated glyph (REQ-I-04). */
    val BookOpen = nextPageIcon("BookOpen") {
        strokePath {
            moveTo(12f, 6f); curveTo(8f, 3.5f, 5.5f, 3.5f, 3f, 5f); lineTo(3f, 18.5f)
            curveTo(5.5f, 17f, 8f, 17f, 12f, 19.5f)
            curveTo(16f, 17f, 18.5f, 17f, 21f, 18.5f); lineTo(21f, 5f)
            curveTo(18.5f, 3.5f, 16f, 3.5f, 12f, 6f)
            moveTo(12f, 6f); lineTo(12f, 19.5f)
        }
    }

    /** BookDetail route icon. Source: `ic_nav_home` placeholder — dedicated glyph (REQ-I-04). */
    val Book = nextPageIcon("Book") {
        strokePath {
            moveTo(6f, 4f); lineTo(18f, 4f); curveTo(19.1f, 4f, 20f, 4.9f, 20f, 6f); lineTo(20f, 18f)
            curveTo(20f, 19.1f, 19.1f, 20f, 18f, 20f); lineTo(6f, 20f)
            curveTo(4.9f, 20f, 4f, 19.1f, 4f, 18f); lineTo(4f, 6f)
            curveTo(4f, 4.9f, 4.9f, 4f, 6f, 4f)
            moveTo(12f, 4f); lineTo(12f, 20f)
        }
    }

    /** Auth route icon. Source: `ic_nav_home` placeholder — dedicated glyph (REQ-I-04). */
    val Person = nextPageIcon("Person") {
        strokePath {
            moveTo(9.5f, 6.5f); arcTo(2.5f, 2.5f, 0f, true, true, 14.5f, 6.5f); arcTo(2.5f, 2.5f, 0f, true, true, 9.5f, 6.5f)
            moveTo(5f, 20f); curveTo(5f, 15.8f, 7.6f, 13.8f, 12f, 13.8f); curveTo(16.4f, 13.8f, 19f, 15.8f, 19f, 20f)
        }
    }

    // ── PR2a sweep constants (Home/Library/Highlights + shared molecules) ──

    /** Search field magnifier. Source: `Icons.Outlined.Search` / `Icons.Default.Search`. */
    val Search = nextPageIcon("Search") {
        strokePath {
            moveTo(6f, 10f); arcTo(4f, 4f, 0f, true, true, 14f, 10f); arcTo(4f, 4f, 0f, true, true, 6f, 10f)
            moveTo(13f, 13f); lineTo(18f, 18f)
        }
    }

    /** Dismiss/clear X. Source: `Icons.Outlined.Close` / `Icons.Default.Close`. */
    val Close = nextPageIcon("Close") {
        strokePath {
            moveTo(6f, 6f); lineTo(18f, 18f)
            moveTo(18f, 6f); lineTo(6f, 18f)
        }
    }

    /** Vertical overflow menu — three dots. Source: `Icons.Default.MoreVert` / `Icons.Filled.MoreVert`. */
    val MoreVert = nextPageIcon("MoreVert") {
        strokePath {
            moveTo(12f, 4.8f); arcTo(1.2f, 1.2f, 0f, true, true, 12f, 7.2f); arcTo(1.2f, 1.2f, 0f, true, true, 12f, 4.8f)
            moveTo(12f, 10.8f); arcTo(1.2f, 1.2f, 0f, true, true, 12f, 13.2f); arcTo(1.2f, 1.2f, 0f, true, true, 12f, 10.8f)
            moveTo(12f, 16.8f); arcTo(1.2f, 1.2f, 0f, true, true, 12f, 19.2f); arcTo(1.2f, 1.2f, 0f, true, true, 12f, 16.8f)
        }
    }

    /** Add plus. Source: `Icons.Outlined.Add`. */
    val Add = nextPageIcon("Add") {
        strokePath {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(5f, 12f); lineTo(19f, 12f)
        }
    }

    /** Filter funnel — three narrowing lines. Source: `Icons.Outlined.FilterList`. */
    val FilterList = nextPageIcon("FilterList") {
        strokePath {
            moveTo(3f, 6f); lineTo(21f, 6f)
            moveTo(6f, 12f); lineTo(18f, 12f)
            moveTo(9f, 18f); lineTo(15f, 18f)
        }
    }

    /** Notification bell. Source: `Icons.Outlined.Notifications`. */
    val Notifications = nextPageIcon("Notifications") {
        strokePath {
            moveTo(12f, 4f); curveTo(8f, 4f, 6f, 6.5f, 6f, 10f); lineTo(6f, 14f); lineTo(4f, 16f); lineTo(20f, 16f)
            lineTo(18f, 14f); lineTo(18f, 10f); curveTo(18f, 6.5f, 16f, 4f, 12f, 4f)
            moveTo(10.5f, 19f); curveTo(10.5f, 20.5f, 13.5f, 20.5f, 13.5f, 19f)
        }
    }

    /** Grid of four rounded squares (library view toggle). Source: `Icons.Outlined.GridView`. */
    val GridView = nextPageIcon("GridView") {
        strokePath {
            moveTo(5f, 5f); lineTo(10f, 5f); lineTo(10f, 10f); lineTo(5f, 10f); lineTo(5f, 5f)
            moveTo(14f, 5f); lineTo(19f, 5f); lineTo(19f, 10f); lineTo(14f, 10f); lineTo(14f, 5f)
            moveTo(5f, 14f); lineTo(10f, 14f); lineTo(10f, 19f); lineTo(5f, 19f); lineTo(5f, 14f)
            moveTo(14f, 14f); lineTo(19f, 14f); lineTo(19f, 19f); lineTo(14f, 19f); lineTo(14f, 14f)
        }
    }

    /** List rows with left gutter (library view toggle). RTL mirrors. Source: `Icons.AutoMirrored.Outlined.ViewList`. */
    val ViewList = nextPageIcon("ViewList", autoMirror = true) {
        strokePath {
            moveTo(4f, 6.5f); lineTo(7f, 6.5f)
            moveTo(9f, 6.5f); lineTo(20f, 6.5f)
            moveTo(4f, 12f); lineTo(7f, 12f)
            moveTo(9f, 12f); lineTo(20f, 12f)
            moveTo(4f, 17.5f); lineTo(7f, 17.5f)
            moveTo(9f, 17.5f); lineTo(20f, 17.5f)
        }
    }

    /** Clock face with hands. Source: `Icons.Outlined.Schedule`. */
    val Clock = nextPageIcon("Clock") {
        strokePath {
            moveTo(4f, 12f); arcTo(8f, 8f, 0f, true, true, 20f, 12f); arcTo(8f, 8f, 0f, true, true, 4f, 12f)
            moveTo(12f, 12f); lineTo(12f, 8f)
            moveTo(12f, 12f); lineTo(15f, 14f)
        }
    }

    /** Ascending trend line. RTL mirrors. Source: `Icons.AutoMirrored.Outlined.ShowChart`. */
    val ChartLine = nextPageIcon("ChartLine", autoMirror = true) {
        strokePath {
            moveTo(3f, 18f); lineTo(9f, 12f); lineTo(13f, 15f); lineTo(21f, 7f)
            moveTo(3f, 21f); lineTo(21f, 21f)
        }
    }

    /** Three vertical bars on a baseline. Source: `Icons.Outlined.BarChart`. */
    val ChartBar = nextPageIcon("ChartBar") {
        strokePath {
            moveTo(4f, 20f); lineTo(20f, 20f)
            moveTo(8f, 20f); lineTo(8f, 14f)
            moveTo(12f, 20f); lineTo(12f, 7f)
            moveTo(16f, 20f); lineTo(16f, 11f)
        }
    }

    /** Bookmark ribbon. Source: `Icons.Outlined.Bookmark`. */
    val Bookmark = nextPageIcon("Bookmark") {
        strokePath {
            moveTo(7f, 4f); lineTo(17f, 4f); lineTo(17f, 20f); lineTo(12f, 16f); lineTo(7f, 20f); lineTo(7f, 4f)
        }
    }

    /** Upload — tray with up arrow. Source: `Icons.Outlined.UploadFile`. */
    val Upload = nextPageIcon("Upload") {
        strokePath {
            moveTo(4f, 15f); lineTo(4f, 19f); lineTo(20f, 19f); lineTo(20f, 15f)
            moveTo(12f, 14f); lineTo(12f, 6f)
            moveTo(8f, 10f); lineTo(12f, 6f); lineTo(16f, 10f)
        }
    }

    /** Four-point sparkle star. Source: `Icons.Outlined.AutoAwesome`. */
    val Sparkle = nextPageIcon("Sparkle") {
        strokePath {
            moveTo(12f, 4f); lineTo(14f, 10f); lineTo(20f, 12f); lineTo(14f, 14f); lineTo(12f, 20f)
            lineTo(10f, 14f); lineTo(4f, 12f); lineTo(10f, 10f); lineTo(12f, 4f)
        }
    }

    /** Quotation marks — two comma glyphs. Source: `Icons.Outlined.FormatQuote`. */
    val Quote = nextPageIcon("Quote") {
        strokePath {
            moveTo(5.5f, 9f); arcTo(2.2f, 2.2f, 0f, true, true, 9.9f, 9f); arcTo(2.2f, 2.2f, 0f, true, true, 5.5f, 9f)
            moveTo(7.7f, 11.2f); lineTo(7.7f, 14.5f)
            moveTo(14.1f, 9f); arcTo(2.2f, 2.2f, 0f, true, true, 18.5f, 9f); arcTo(2.2f, 2.2f, 0f, true, true, 14.1f, 9f)
            moveTo(16.3f, 11.2f); lineTo(16.3f, 14.5f)
        }
    }

    /** Lightbulb — dome, base, filament. Source: `Icons.Outlined.Lightbulb`. */
    val Lightbulb = nextPageIcon("Lightbulb") {
        strokePath {
            moveTo(12f, 3.5f); curveTo(7.5f, 3.5f, 5f, 6.5f, 5f, 10f); curveTo(5f, 13f, 7f, 14.5f, 7f, 17f); lineTo(17f, 17f)
            curveTo(17f, 14.5f, 19f, 13f, 19f, 10f); curveTo(19f, 6.5f, 16.5f, 3.5f, 12f, 3.5f)
            moveTo(8.5f, 19f); lineTo(15.5f, 19f)
            moveTo(9.5f, 21f); lineTo(14.5f, 21f)
        }
    }

    /** Check mark. Source: `Icons.Outlined.Check` / `Icons.Filled.Check`. */
    val Check = nextPageIcon("Check") {
        strokePath {
            moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f)
        }
    }

    /** Error outline — circle with exclamation. Source: `Icons.Outlined.ErrorOutline`. */
    val ErrorOutline = nextPageIcon("ErrorOutline") {
        strokePath {
            moveTo(4f, 12f); arcTo(8f, 8f, 0f, true, true, 20f, 12f); arcTo(8f, 8f, 0f, true, true, 4f, 12f)
            moveTo(12f, 7f); lineTo(12f, 13f)
            moveTo(11.2f, 17f); arcTo(0.8f, 0.8f, 0f, true, true, 12.8f, 17f); arcTo(0.8f, 0.8f, 0f, true, true, 11.2f, 17f)
        }
    }

    /** Trophy cup. Source: `Icons.Outlined.EmojiEvents`. */
    val Trophy = nextPageIcon("Trophy") {
        strokePath {
            moveTo(7f, 4f); lineTo(17f, 4f); lineTo(17f, 9f); curveTo(17f, 12.5f, 14.8f, 14.5f, 12f, 14.5f)
            curveTo(9.2f, 14.5f, 7f, 12.5f, 7f, 9f); lineTo(7f, 4f)
            moveTo(7f, 6.5f); curveTo(4.5f, 6.5f, 4.5f, 11f, 7f, 11f)
            moveTo(17f, 6.5f); curveTo(19.5f, 6.5f, 19.5f, 11f, 17f, 11f)
            moveTo(12f, 14.5f); lineTo(12f, 18f)
            moveTo(9f, 20f); lineTo(15f, 20f)
        }
    }

    /** Paint palette — oval with thumb hole and dots. Source: `Icons.Outlined.Palette` / `Icons.Default.Palette`. */
    val Palette = nextPageIcon("Palette") {
        strokePath {
            moveTo(12f, 4f); curveTo(6.5f, 4f, 3f, 7.5f, 3f, 12f); curveTo(3f, 16.5f, 6.5f, 20f, 12f, 20f)
            curveTo(17.5f, 20f, 21f, 16.5f, 21f, 12f); curveTo(21f, 9.8f, 19.8f, 8.4f, 17.8f, 8.4f)
            curveTo(16.3f, 8.4f, 15.7f, 9.4f, 15.7f, 10.4f); curveTo(15.7f, 12.4f, 13.7f, 12.4f, 13.7f, 10.4f)
            moveTo(7.5f, 10.5f); arcTo(1f, 1f, 0f, true, true, 9.5f, 10.5f); arcTo(1f, 1f, 0f, true, true, 7.5f, 10.5f)
            moveTo(9f, 15.5f); arcTo(1f, 1f, 0f, true, true, 11f, 15.5f); arcTo(1f, 1f, 0f, true, true, 9f, 15.5f)
            moveTo(14.5f, 16f); arcTo(1f, 1f, 0f, true, true, 16.5f, 16f); arcTo(1f, 1f, 0f, true, true, 14.5f, 16f)
        }
    }

    /** Copy — two overlapping squares. Source: `Icons.Default.ContentCopy` / `Icons.Filled.ContentCopy`. */
    val Copy = nextPageIcon("Copy") {
        strokePath {
            moveTo(10f, 6f); lineTo(18f, 6f); lineTo(18f, 14f); lineTo(10f, 14f); lineTo(10f, 6f)
            moveTo(6f, 10f); lineTo(14f, 10f); lineTo(14f, 18f); lineTo(6f, 18f); lineTo(6f, 10f)
        }
    }

    /** Label tag with hole. RTL mirrors. Source: `Icons.AutoMirrored.Filled.Label`. */
    val Tag = nextPageIcon("Tag", autoMirror = true) {
        strokePath {
            moveTo(4f, 4f); lineTo(14f, 4f); lineTo(20f, 12f); lineTo(12f, 20f); lineTo(4f, 14f); lineTo(4f, 4f)
            moveTo(7.2f, 8.5f); arcTo(1.3f, 1.3f, 0f, true, true, 9.8f, 8.5f); arcTo(1.3f, 1.3f, 0f, true, true, 7.2f, 8.5f)
        }
    }

    /** Pencil — diagonal body with tip. Source: `Icons.Filled.EditNote`. */
    val Pencil = nextPageIcon("Pencil") {
        strokePath {
            moveTo(4f, 20f); lineTo(5.5f, 15f); lineTo(15f, 5.5f); lineTo(18.5f, 9f); lineTo(9f, 18.5f); lineTo(4f, 20f)
            moveTo(4.8f, 16.8f); lineTo(7.2f, 19.2f)
        }
    }

    /** Share — three connected dots. Source: `Icons.Default.Share` / `Icons.Filled.Share`. */
    val Share = nextPageIcon("Share") {
        strokePath {
            moveTo(10f, 12f); arcTo(2f, 2f, 0f, true, true, 14f, 12f); arcTo(2f, 2f, 0f, true, true, 10f, 12f)
            moveTo(17.5f, 5f); arcTo(1.5f, 1.5f, 0f, true, true, 20.5f, 5f); arcTo(1.5f, 1.5f, 0f, true, true, 17.5f, 5f)
            moveTo(3.5f, 19f); arcTo(1.5f, 1.5f, 0f, true, true, 6.5f, 19f); arcTo(1.5f, 1.5f, 0f, true, true, 3.5f, 19f)
            moveTo(13.6f, 10.9f); lineTo(18.2f, 6.1f)
            moveTo(10.4f, 13.1f); lineTo(5.8f, 17.9f)
        }
    }

    /** Trash can — lid, body, ribs. Source: `Icons.Filled.Delete` / `Icons.Default.Delete`. */
    val Trash = nextPageIcon("Trash") {
        strokePath {
            moveTo(5f, 6f); lineTo(19f, 6f)
            moveTo(9.5f, 4f); lineTo(14.5f, 4f)
            moveTo(7f, 6f); lineTo(7f, 19f); lineTo(17f, 19f); lineTo(17f, 6f)
            moveTo(10f, 9f); lineTo(10f, 16f)
            moveTo(14f, 9f); lineTo(14f, 16f)
        }
    }

    /** Stacked books. RTL mirrors. Source: `Icons.AutoMirrored.Outlined.LibraryBooks`. */
    val LibraryBooks = nextPageIcon("LibraryBooks", autoMirror = true) {
        strokePath {
            moveTo(4f, 4f); lineTo(20f, 4f)
            moveTo(4f, 12f); lineTo(20f, 12f)
            moveTo(4f, 20f); lineTo(20f, 20f)
            moveTo(6f, 4f); lineTo(6f, 12f)
            moveTo(10f, 4f); lineTo(10f, 12f)
            moveTo(15f, 12f); lineTo(15f, 20f)
        }
    }

    /** Cloud with down arrow. Source: `Icons.Filled.CloudDownload`. */
    val CloudDownload = nextPageIcon("CloudDownload") {
        strokePath {
            moveTo(7f, 17f); curveTo(4.5f, 17f, 3.5f, 15f, 4.5f, 13f); curveTo(5.2f, 11.5f, 7f, 10.8f, 8.5f, 11.2f)
            curveTo(9.5f, 8.6f, 13f, 8f, 14.8f, 9.8f); curveTo(17.5f, 9.8f, 19.5f, 11.8f, 19.5f, 14f)
            curveTo(19.5f, 15.8f, 18.3f, 17f, 16.5f, 17f); lineTo(7f, 17f)
            moveTo(12f, 13.5f); lineTo(12f, 19.5f)
            moveTo(8.5f, 16f); lineTo(12f, 19.5f); lineTo(15.5f, 16f)
        }
    }

    // ── PR2b-1 sweep constants (Settings area) ──

    /** Monitor and phone (settings devices). Source: `Icons.Outlined.Devices` / `Icons.Outlined.DevicesOther`. */
    val Devices = nextPageIcon("Devices") {
        strokePath {
            moveTo(4f, 5f); lineTo(16f, 5f); lineTo(16f, 13f); lineTo(4f, 13f); lineTo(4f, 5f)
            moveTo(10f, 13f); lineTo(10f, 17f)
            moveTo(7f, 17f); lineTo(13f, 17f)
            moveTo(18f, 7f); lineTo(20f, 7f); lineTo(20f, 19f); lineTo(18f, 19f); lineTo(18f, 7f)
        }
    }

    /** Crescent moon. Source: `Icons.Outlined.DarkMode`. */
    val DarkMode = nextPageIcon("DarkMode") {
        strokePath {
            moveTo(16f, 4f); curveTo(9f, 5.5f, 4.5f, 10.5f, 5f, 16f)
            curveTo(5.5f, 21f, 10f, 23f, 14.5f, 22f)
            curveTo(12.5f, 20.5f, 11.5f, 18f, 11.5f, 15f)
            curveTo(11.5f, 11f, 13.5f, 7f, 16f, 4f)
        }
    }

    /** Sun with rays. Source: `Icons.Outlined.LightMode`. */
    val LightMode = nextPageIcon("LightMode") {
        strokePath {
            moveTo(8.5f, 12f); arcTo(3.5f, 3.5f, 0f, true, true, 15.5f, 12f); arcTo(3.5f, 3.5f, 0f, true, true, 8.5f, 12f)
            moveTo(12f, 4.5f); lineTo(12f, 7f)
            moveTo(12f, 17f); lineTo(12f, 19.5f)
            moveTo(4.5f, 12f); lineTo(7f, 12f)
            moveTo(17f, 12f); lineTo(19.5f, 12f)
        }
    }

    /** Brightness auto — sun inside a ring. Source: `Icons.Outlined.SettingsBrightness`. */
    val BrightnessAuto = nextPageIcon("BrightnessAuto") {
        strokePath {
            moveTo(4.5f, 12f); arcTo(7.5f, 7.5f, 0f, true, true, 19.5f, 12f); arcTo(7.5f, 7.5f, 0f, true, true, 4.5f, 12f)
            moveTo(5f, 5f); lineTo(6.8f, 6.8f)
            moveTo(19f, 5f); lineTo(17.2f, 6.8f)
            moveTo(19f, 19f); lineTo(17.2f, 17.2f)
            moveTo(5f, 19f); lineTo(6.8f, 17.2f)
        }
    }

    /** Globe with meridians. Source: `Icons.Outlined.Language`. */
    val Language = nextPageIcon("Language") {
        strokePath {
            moveTo(4f, 12f); arcTo(8f, 8f, 0f, true, true, 20f, 12f); arcTo(8f, 8f, 0f, true, true, 4f, 12f)
            moveTo(5f, 12f); curveTo(5f, 16f, 19f, 16f, 19f, 12f); curveTo(19f, 8f, 5f, 8f, 5f, 12f)
            moveTo(12f, 4f); lineTo(12f, 20f)
        }
    }

    /** Storage cylinder. Source: `Icons.Outlined.Storage`. */
    val Storage = nextPageIcon("Storage") {
        strokePath {
            moveTo(4f, 7f); curveTo(4f, 4.8f, 20f, 4.8f, 20f, 7f); curveTo(20f, 9.2f, 4f, 9.2f, 4f, 7f)
            moveTo(4f, 7f); lineTo(4f, 17f)
            moveTo(20f, 7f); lineTo(20f, 17f)
            moveTo(4f, 17f); curveTo(4f, 19.2f, 20f, 19.2f, 20f, 17f)
        }
    }

    /** Info circle with exclamation. Source: `Icons.Outlined.Info`. */
    val Info = nextPageIcon("Info") {
        strokePath {
            moveTo(4f, 12f); arcTo(8f, 8f, 0f, true, true, 20f, 12f); arcTo(8f, 8f, 0f, true, true, 4f, 12f)
            moveTo(12f, 11f); lineTo(12f, 17f)
            moveTo(11.2f, 8f); arcTo(0.8f, 0.8f, 0f, true, true, 12.8f, 8f); arcTo(0.8f, 0.8f, 0f, true, true, 11.2f, 8f)
        }
    }

    /** Bug with legs and antennae. Source: `Icons.Outlined.BugReport` / `Icons.Default.BugReport`. */
    val BugReport = nextPageIcon("BugReport") {
        strokePath {
            moveTo(12f, 7f); curveTo(8f, 7f, 6.5f, 9.5f, 6.5f, 12f); curveTo(6.5f, 14.5f, 8f, 17f, 12f, 17f)
            curveTo(16f, 17f, 17.5f, 14.5f, 17.5f, 12f); curveTo(17.5f, 9.5f, 16f, 7f, 12f, 7f)
            moveTo(10f, 4.5f); lineTo(12f, 6.5f); lineTo(14f, 4.5f)
            moveTo(6.5f, 10f); lineTo(4f, 9f)
            moveTo(6.5f, 12f); lineTo(4f, 12f)
            moveTo(6.5f, 14f); lineTo(4f, 15f)
            moveTo(17.5f, 10f); lineTo(20f, 9f)
            moveTo(17.5f, 12f); lineTo(20f, 12f)
            moveTo(17.5f, 14f); lineTo(20f, 15f)
        }
    }

    /** Sign-out — door with exit arrow. RTL mirrors. Source: `Icons.AutoMirrored.Outlined.ExitToApp`. */
    val SignOut = nextPageIcon("SignOut", autoMirror = true) {
        strokePath {
            moveTo(5f, 4f); lineTo(13f, 4f); lineTo(13f, 20f); lineTo(5f, 20f); lineTo(5f, 4f)
            moveTo(9f, 12f); lineTo(18f, 12f)
            moveTo(15f, 9f); lineTo(18f, 12f); lineTo(15f, 15f)
        }
    }

    /** Smartphone. Source: `Icons.Outlined.PhoneAndroid`. */
    val Smartphone = nextPageIcon("Smartphone") {
        strokePath {
            moveTo(8f, 3f); lineTo(16f, 3f); lineTo(16f, 21f); lineTo(8f, 21f); lineTo(8f, 3f)
            moveTo(8f, 7f); lineTo(16f, 7f)
            moveTo(11f, 18.5f); lineTo(13f, 18.5f)
        }
    }

    /** Desktop monitor with stand. Source: `Icons.Outlined.DesktopWindows`. */
    val Monitor = nextPageIcon("Monitor") {
        strokePath {
            moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 15f); lineTo(4f, 15f); lineTo(4f, 5f)
            moveTo(12f, 15f); lineTo(12f, 19f)
            moveTo(8f, 19f); lineTo(16f, 19f)
        }
    }

    /** Laptop — screen over base. Source: `Icons.Outlined.DesktopMac`. */
    val Laptop = nextPageIcon("Laptop") {
        strokePath {
            moveTo(5f, 5f); lineTo(19f, 5f); lineTo(19f, 13f); lineTo(5f, 13f); lineTo(5f, 5f)
            moveTo(3f, 17f); lineTo(21f, 17f); lineTo(19.5f, 13.5f); lineTo(4.5f, 13.5f)
        }
    }

    /** Chevron (settings row affordance). RTL mirrors. Source: `Icons.Default.ChevronRight`. */
    val ChevronRight = nextPageIcon("ChevronRight", autoMirror = true) {
        strokePath {
            moveTo(9f, 7f); curveTo(11f, 9f, 13f, 9f, 15f, 12f); curveTo(13f, 15f, 11f, 15f, 9f, 17f)
        }
    }

    /** Back arrow. RTL mirrors. Source: `Icons.AutoMirrored.Filled.ArrowBack` / `Icons.AutoMirrored.Outlined.ArrowBack`. */
    val ArrowBack = nextPageIcon("ArrowBack", autoMirror = true) {
        strokePath {
            moveTo(20f, 12f); lineTo(5f, 12f)
            moveTo(10f, 6f); lineTo(5f, 12f); lineTo(10f, 18f)
        }
    }

    /** Sharp right chevron (settings row affordance). RTL mirrors. Source: `Icons.AutoMirrored.Outlined.KeyboardArrowRight`. */
    val ArrowRight = nextPageIcon("ArrowRight", autoMirror = true) {
        strokePath {
            moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f)
        }
    }

    /** Sync — two chasing arrows. Source: `Icons.Default.Sync`. */
    val Sync = nextPageIcon("Sync") {
        strokePath {
            moveTo(5.5f, 12f); curveTo(5.5f, 8.3f, 8.3f, 5.5f, 12f, 5.5f); curveTo(15.7f, 5.5f, 18.5f, 8.3f, 18.5f, 12f)
            moveTo(18.5f, 12f); lineTo(16.2f, 10.6f)
            moveTo(18.5f, 12f); curveTo(18.5f, 15.7f, 15.7f, 18.5f, 12f, 18.5f); curveTo(8.3f, 18.5f, 5.5f, 15.7f, 5.5f, 12f)
            moveTo(5.5f, 12f); lineTo(7.8f, 13.4f)
        }
    }

    /** Cloud with sync arrows. Source: `Icons.Outlined.CloudSync`. */
    val CloudSync = nextPageIcon("CloudSync") {
        strokePath {
            moveTo(7f, 11.5f); curveTo(4.5f, 11.5f, 3.5f, 9.7f, 4.4f, 8.1f)
            curveTo(5.1f, 6.9f, 6.6f, 6.4f, 7.8f, 6.8f)
            curveTo(8.8f, 5f, 11.6f, 4.7f, 13f, 6.1f)
            curveTo(15.4f, 6.4f, 17f, 8.1f, 17f, 10.1f)
            moveTo(8.5f, 14.8f); curveTo(10f, 16.8f, 13.5f, 17f, 15.5f, 15.5f)
            moveTo(15.5f, 15.5f); lineTo(13.5f, 15.6f)
            moveTo(15.5f, 15.5f); curveTo(14f, 13.5f, 10.5f, 13.3f, 8.5f, 14.8f)
            moveTo(8.5f, 14.8f); lineTo(10.5f, 14.7f)
        }
    }

    // ── PR2b-2 sweep constants (Reader/BookDetail/SplitSettings/Dictionary) ──

    /** "Aa" text glyph. Source: `Icons.Default.FontDownload`. */
    val TextAa = nextPageIcon("TextAa") {
        strokePath {
            moveTo(6f, 19f); lineTo(11f, 6f); lineTo(16f, 19f)
            moveTo(8.3f, 14.5f); lineTo(13.7f, 14.5f)
            moveTo(19f, 11f); curveTo(19f, 9.3f, 17.7f, 8f, 16f, 8f); curveTo(14.3f, 8f, 13f, 9.3f, 13f, 11f)
            curveTo(13f, 12.7f, 14.3f, 14f, 16f, 14f); curveTo(17.7f, 14f, 19f, 12.7f, 19f, 11f)
            moveTo(19f, 11f); lineTo(19f, 14.5f)
        }
    }

    /** Left-aligned text lines. RTL mirrors. Source: `Icons.AutoMirrored.Filled.FormatAlignLeft`. */
    val AlignLeft = nextPageIcon("AlignLeft", autoMirror = true) {
        strokePath {
            moveTo(4f, 6f); lineTo(20f, 6f)
            moveTo(4f, 10f); lineTo(14f, 10f)
            moveTo(4f, 14f); lineTo(20f, 14f)
            moveTo(4f, 18f); lineTo(14f, 18f)
        }
    }

    /** Center-aligned text lines. Source: `Icons.Default.FormatAlignCenter`. */
    val AlignCenter = nextPageIcon("AlignCenter") {
        strokePath {
            moveTo(4f, 6f); lineTo(20f, 6f)
            moveTo(7f, 10f); lineTo(17f, 10f)
            moveTo(4f, 14f); lineTo(20f, 14f)
            moveTo(7f, 18f); lineTo(17f, 18f)
        }
    }

    /** Right-aligned text lines. RTL mirrors. Source: `Icons.AutoMirrored.Filled.FormatAlignRight`. */
    val AlignRight = nextPageIcon("AlignRight", autoMirror = true) {
        strokePath {
            moveTo(4f, 6f); lineTo(20f, 6f)
            moveTo(10f, 10f); lineTo(20f, 10f)
            moveTo(4f, 14f); lineTo(20f, 14f)
            moveTo(10f, 18f); lineTo(20f, 18f)
        }
    }

    /** Justified text lines. Source: `Icons.Default.FormatAlignJustify`. */
    val AlignJustify = nextPageIcon("AlignJustify") {
        strokePath {
            moveTo(4f, 6f); lineTo(20f, 6f)
            moveTo(4f, 10f); lineTo(20f, 10f)
            moveTo(4f, 14f); lineTo(20f, 14f)
            moveTo(4f, 18f); lineTo(20f, 18f)
        }
    }

    /** Left chevron arrow. RTL mirrors. Source: `Icons.AutoMirrored.Filled.KeyboardArrowLeft`. */
    val ArrowLeft = nextPageIcon("ArrowLeft", autoMirror = true) {
        strokePath {
            moveTo(15f, 6f); lineTo(9f, 12f); lineTo(15f, 18f)
        }
    }

    /** Forward (right) arrow. RTL mirrors. Source: `Icons.AutoMirrored.Filled.ArrowForward`. */
    val ArrowForward = nextPageIcon("ArrowForward", autoMirror = true) {
        strokePath {
            moveTo(4f, 12f); lineTo(19f, 12f)
            moveTo(14f, 6f); lineTo(19f, 12f); lineTo(14f, 18f)
        }
    }

    /** "A" with up arrow (font size). Source: `Icons.Default.TextIncrease`. */
    val TextSize = nextPageIcon("TextSize") {
        strokePath {
            moveTo(5f, 20f); lineTo(10.5f, 5f); lineTo(16f, 20f)
            moveTo(7.7f, 15f); lineTo(13.3f, 15f)
            moveTo(19.5f, 4f); lineTo(19.5f, 12f)
            moveTo(16.5f, 7f); lineTo(19.5f, 4f); lineTo(22.5f, 7f)
        }
    }

    /** Bullet list with dots. RTL mirrors. Source: `Icons.AutoMirrored.Filled.Toc`. */
    val ListBullets = nextPageIcon("ListBullets", autoMirror = true) {
        strokePath {
            moveTo(4.6f, 7f); arcTo(0.9f, 0.9f, 0f, true, true, 6.4f, 7f); arcTo(0.9f, 0.9f, 0f, true, true, 4.6f, 7f)
            moveTo(9f, 7f); lineTo(20f, 7f)
            moveTo(4.6f, 12f); arcTo(0.9f, 0.9f, 0f, true, true, 6.4f, 12f); arcTo(0.9f, 0.9f, 0f, true, true, 4.6f, 12f)
            moveTo(9f, 12f); lineTo(20f, 12f)
            moveTo(4.6f, 17f); arcTo(0.9f, 0.9f, 0f, true, true, 6.4f, 17f); arcTo(0.9f, 0.9f, 0f, true, true, 4.6f, 17f)
            moveTo(9f, 17f); lineTo(20f, 17f)
        }
    }

    /** Filled five-point star (rated). Family exception: filled path via [fillPath]. Source: `Icons.Filled.Star`. */
    val Star = nextPageIcon("Star") {
        fillPath {
            moveTo(12f, 4f); lineTo(14.5f, 9.5f); lineTo(20.5f, 10.2f); lineTo(16f, 14.2f); lineTo(17.3f, 20f)
            lineTo(12f, 17f); lineTo(6.7f, 20f); lineTo(8f, 14.2f); lineTo(3.5f, 10.2f); lineTo(9.5f, 9.5f); close()
        }
    }

    /** Outlined five-point star (unrated). Source: `Icons.Outlined.Star`. */
    val StarBorder = nextPageIcon("StarBorder") {
        strokePath {
            moveTo(12f, 4f); lineTo(14.5f, 9.5f); lineTo(20.5f, 10.2f); lineTo(16f, 14.2f); lineTo(17.3f, 20f)
            lineTo(12f, 17f); lineTo(6.7f, 20f); lineTo(8f, 14.2f); lineTo(3.5f, 10.2f); lineTo(9.5f, 9.5f)
            lineTo(12f, 4f)
        }
    }

    /** Phone with rotation arrows. Source: `Icons.Default.ScreenRotation`. */
    val ScreenRotation = nextPageIcon("ScreenRotation") {
        strokePath {
            moveTo(7f, 5f); lineTo(17f, 5f); lineTo(17f, 19f); lineTo(7f, 19f); lineTo(7f, 5f)
            moveTo(19f, 8f); curveTo(19f, 6f, 17.5f, 4.5f, 15.5f, 4.5f)
            moveTo(19f, 8f); lineTo(18f, 5.8f)
            moveTo(5f, 16f); curveTo(5f, 18f, 6.5f, 19.5f, 8.5f, 19.5f)
            moveTo(5f, 16f); lineTo(6f, 18.2f)
        }
    }
}
