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
}
