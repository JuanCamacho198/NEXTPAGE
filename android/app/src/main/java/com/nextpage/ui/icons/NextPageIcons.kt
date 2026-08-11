package com.nextpage.ui.icons

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
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
        viewportWidth: Float = 24f,
        viewportHeight: Float = 24f,
        block: ImageVector.Builder.() -> ImageVector.Builder
    ): ImageVector {
        val builder = ImageVector.Builder(
            name = "NextPageIcons.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
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

    /**
     * Bottom nav "Home" tab. Filled exception to the stroke-only family.
     * Source: `ic_home.xml` (filled Material Symbols home, 512 viewport).
     */
    val Home = nextPageIcon("Home", viewportWidth = 512f, viewportHeight = 512f) {
        fillPath {
            moveTo(256f, 319.84f)
            curveTo(220.65f, 319.84f, 192f, 348.49f, 192f, 383.84f)
            lineTo(192f, 511.84f); lineTo(320f, 511.84f); lineTo(320f, 383.84f)
            curveTo(320f, 348.49f, 291.35f, 319.84f, 256f, 319.84f); close()
            moveTo(362.67f, 383.84f); lineTo(362.67f, 511.84f); lineTo(448f, 511.84f)
            curveTo(483.35f, 511.84f, 512f, 483.19f, 512f, 447.84f); lineTo(512f, 253.26f)
            curveTo(512f, 242.18f, 507.7f, 231.53f, 499.99f, 223.56f)
            lineTo(318.7f, 27.57f)
            curveTo(286.71f, -7.04f, 232.72f, -9.17f, 198.11f, 22.82f)
            curveTo(196.47f, 24.34f, 194.88f, 25.92f, 193.36f, 27.57f); lineTo(12.4f, 223.5f)
            curveTo(4.45f, 231.5f, 0f, 242.31f, 0f, 253.58f); lineTo(0f, 447.84f)
            curveTo(0f, 483.19f, 28.65f, 511.84f, 64f, 511.84f)
            lineTo(149.33f, 511.84f); lineTo(149.33f, 383.84f)
            curveTo(149.73f, 325.67f, 196.7f, 278.16f, 253.4f, 276.8f)
            curveTo(312.01f, 275.38f, 362.22f, 323.7f, 362.67f, 383.84f); close()
        }
    }

    /**
     * Bottom nav "Library" tab — bookshelf with two rows of books. Filled exception
     * to the stroke-only family. Source: `ic_library.xml` (filled library books).
     */
    val Library = nextPageIcon("Library") {
        fillPath {
            moveTo(8f, 12f)
            curveTo(8f, 12.55f, 7.55f, 13f, 7f, 13f); lineTo(5f, 13f); lineTo(5f, 15f)
            curveTo(5f, 15.55f, 4.55f, 16f, 4f, 16f)
            curveTo(3.45f, 16f, 3f, 15.55f, 3f, 15f); lineTo(3f, 13f); lineTo(1f, 13f)
            curveTo(0.45f, 13f, 0f, 12.55f, 0f, 12f)
            curveTo(0f, 11.45f, 0.45f, 11f, 1f, 11f); lineTo(3f, 11f); lineTo(3f, 9f)
            curveTo(3f, 8.45f, 3.45f, 8f, 4f, 8f)
            curveTo(4.55f, 8f, 5f, 8.45f, 5f, 9f); lineTo(5f, 11f); lineTo(7f, 11f)
            curveTo(7.55f, 11f, 8f, 11.45f, 8f, 12f); close()
            moveTo(3f, 21f); curveTo(3f, 22.66f, 4.34f, 24f, 6f, 24f); lineTo(8f, 24f); lineTo(8f, 19f)
            lineTo(3f, 19f); lineTo(3f, 21f); close()
            moveTo(3f, 3f); lineTo(3f, 5f); lineTo(8f, 5f); lineTo(8f, 0f); lineTo(6f, 0f)
            curveTo(4.34f, 0f, 3f, 1.34f, 3f, 3f); close()
            moveTo(10f, 17f); lineTo(15f, 17f); lineTo(15f, 7f); lineTo(10f, 7f); lineTo(10f, 17f); close()
            moveTo(10f, 24f); lineTo(12f, 24f); curveTo(13.66f, 24f, 15f, 22.66f, 15f, 21f)
            lineTo(15f, 19f); lineTo(10f, 19f); lineTo(10f, 24f); close()
            moveTo(20.94f, 6.09f); lineTo(15.34f, 7.16f); lineTo(17.51f, 18.01f); lineTo(23.11f, 16.95f)
            lineTo(20.94f, 6.09f); close()
            moveTo(23.97f, 21.31f); lineTo(23.49f, 18.92f); lineTo(17.89f, 19.98f); lineTo(18.37f, 22.38f)
            curveTo(18.57f, 23.46f, 19.62f, 24.17f, 20.7f, 23.97f)
            lineTo(22.38f, 23.65f); curveTo(23.46f, 23.44f, 24.17f, 22.39f, 23.97f, 21.31f); close()
            moveTo(20.57f, 4.13f); lineTo(20.09f, 1.63f)
            curveTo(19.88f, 0.54f, 18.84f, -0.17f, 17.75f, 0.04f)
            lineTo(16.08f, 0.35f)
            curveTo(15.4f, 0.48f, 14.86f, 0.95f, 14.61f, 1.55f)
            curveTo(14.1f, 0.63f, 13.13f, 0f, 12f, 0f); lineTo(10f, 0f); lineTo(10f, 5f)
            lineTo(15.97f, 5f); lineTo(20.57f, 4.13f); close()
        }
    }

    /**
     * Bottom nav "Highlights" tab — highlighter marker. Filled exception to the
     * stroke-only family. Source: `ic_highlights.xml` (filled highlight/pen).
     */
    val Highlights = nextPageIcon("Highlights") {
        fillPath {
            moveTo(8.02f, 19f)
            curveTo(9.8f, 19f, 11.5f, 18.24f, 12.72f, 16.87f); lineTo(19.82f, 8.35f)
            curveTo(21.47f, 6.45f, 21.37f, 3.56f, 19.59f, 1.77f); lineTo(19.22f, 1.4f)
            curveTo(17.44f, -0.38f, 14.55f, -0.48f, 12.67f, 1.15f); lineTo(4.09f, 8.31f)
            curveTo(2.76f, 9.49f, 2f, 11.19f, 2f, 12.97f); lineTo(2f, 16.87f)
            lineTo(0.44f, 18.43f)
            curveTo(-0.15f, 19.02f, -0.15f, 19.97f, 0.44f, 20.55f)
            curveTo(0.73f, 20.84f, 1.12f, 20.99f, 1.5f, 20.99f)
            curveTo(1.88f, 20.99f, 2.27f, 20.84f, 2.56f, 20.55f); lineTo(4.12f, 18.99f)
            lineTo(8.02f, 18.99f); close()
            moveTo(6.05f, 10.59f); lineTo(14.61f, 3.45f)
            curveTo(15.33f, 2.83f, 16.42f, 2.86f, 17.1f, 3.54f); lineTo(17.47f, 3.91f)
            curveTo(18.14f, 4.58f, 18.18f, 5.68f, 17.54f, 6.42f); lineTo(10.45f, 14.92f)
            curveTo(10.22f, 15.17f, 9.96f, 15.39f, 9.67f, 15.56f); lineTo(5.45f, 11.34f)
            curveTo(5.61f, 11.06f, 5.82f, 10.81f, 6.05f, 10.59f); close()
            moveTo(24f, 22.5f); curveTo(24f, 23.33f, 23.33f, 24f, 22.5f, 24f)
            lineTo(5.5f, 24f); curveTo(4.67f, 24f, 4f, 23.33f, 4f, 22.5f)
            curveTo(4f, 21.67f, 4.67f, 21f, 5.5f, 21f); lineTo(22.5f, 21f)
            curveTo(23.33f, 21f, 24f, 21.67f, 24f, 22.5f); close()
        }
    }

    /**
     * Bottom nav "Settings" tab — gear. Filled exception to the stroke-only family.
     * Source: `ic_settings.xml` (filled Material Symbols settings, 512 viewport).
     */
    val Settings = nextPageIcon("Settings", viewportWidth = 512f, viewportHeight = 512f) {
        fillPath {
            moveTo(34.28f, 384f)
            curveTo(51.93f, 414.63f, 91.06f, 425.15f, 121.68f, 407.5f)
            curveTo(121.7f, 407.49f, 121.72f, 407.48f, 121.74f, 407.46f); lineTo(131.23f, 401.98f)
            curveTo(149.15f, 417.31f, 169.75f, 429.2f, 191.99f, 437.05f); lineTo(191.99f, 448f)
            curveTo(191.99f, 483.35f, 220.64f, 512f, 255.99f, 512f)
            curveTo(291.34f, 512f, 319.99f, 483.35f, 319.99f, 448f); lineTo(319.99f, 437.06f)
            curveTo(342.23f, 429.2f, 362.83f, 417.29f, 380.75f, 401.94f); lineTo(390.29f, 407.44f)
            curveTo(420.92f, 425.11f, 460.08f, 414.61f, 477.76f, 383.97f)
            curveTo(495.43f, 353.34f, 484.93f, 314.18f, 454.29f, 296.5f); lineTo(454.29f, 296.5f)
            lineTo(444.82f, 291.04f)
            curveTo(449.08f, 267.84f, 449.08f, 244.05f, 444.82f, 220.85f); lineTo(454.29f, 215.39f)
            curveTo(484.92f, 197.72f, 495.43f, 158.56f, 477.76f, 127.92f)
            curveTo(460.09f, 97.29f, 420.93f, 86.78f, 390.29f, 104.45f); lineTo(380.8f, 109.93f)
            curveTo(362.86f, 94.64f, 342.25f, 82.77f, 320f, 74.94f); lineTo(320f, 64f)
            curveTo(320f, 28.65f, 291.35f, 0f, 256f, 0f)
            curveTo(220.65f, 0f, 192f, 28.65f, 192f, 64f); lineTo(192f, 74.94f)
            curveTo(169.76f, 82.8f, 149.16f, 94.71f, 131.24f, 110.06f); lineTo(121.7f, 104.53f)
            curveTo(91.07f, 86.86f, 51.91f, 97.37f, 34.24f, 128f)
            curveTo(16.57f, 158.63f, 27.07f, 197.79f, 57.71f, 215.47f); lineTo(57.71f, 215.47f)
            lineTo(67.18f, 220.93f)
            curveTo(62.92f, 244.13f, 62.92f, 267.92f, 67.18f, 291.12f); lineTo(57.71f, 296.58f)
            curveTo(27.16f, 314.3f, 16.69f, 353.38f, 34.28f, 384f); close()
            moveTo(256f, 170.67f)
            curveTo(303.13f, 170.67f, 341.33f, 208.88f, 341.33f, 256f)
            curveTo(341.33f, 303.12f, 303.13f, 341.33f, 256f, 341.33f)
            curveTo(208.87f, 341.33f, 170.67f, 303.13f, 170.67f, 256f)
            curveTo(170.67f, 208.87f, 208.87f, 170.67f, 256f, 170.67f); close()
        }
    }

    /**
     * Bottom nav "Statistics" tab — bar chart. Filled exception to the stroke-only
     * family. Source: `ic_stadistics.xml` (filled bar chart, note spelling).
     */
    val Statistics = nextPageIcon("Statistics") {
        fillPath {
            moveTo(23f, 22f); lineTo(5f, 22f)
            arcTo(3f, 3f, 0f, false, true, 2f, 19f); lineTo(2f, 1f)
            arcTo(1f, 1f, 0f, false, false, 0f, 1f); lineTo(0f, 19f)
            arcTo(5.006f, 5.006f, 0f, false, false, 5f, 24f); lineTo(23f, 24f)
            arcTo(1f, 1f, 0f, false, false, 23f, 22f); close()
            moveTo(6f, 20f); arcTo(1f, 1f, 0f, false, false, 7f, 19f); lineTo(7f, 12f)
            arcTo(1f, 1f, 0f, false, false, 5f, 12f); lineTo(5f, 19f)
            arcTo(1f, 1f, 0f, false, false, 6f, 20f); close()
            moveTo(10f, 10f); lineTo(10f, 19f)
            arcTo(1f, 1f, 0f, false, false, 12f, 19f); lineTo(12f, 10f)
            arcTo(1f, 1f, 0f, false, false, 10f, 10f); close()
            moveTo(15f, 13f); lineTo(15f, 19f)
            arcTo(1f, 1f, 0f, false, false, 17f, 19f); lineTo(17f, 13f)
            arcTo(1f, 1f, 0f, false, false, 15f, 13f); close()
            moveTo(20f, 9f); lineTo(20f, 19f)
            arcTo(1f, 1f, 0f, false, false, 22f, 19f); lineTo(22f, 9f)
            arcTo(1f, 1f, 0f, false, false, 20f, 9f); close()
            moveTo(6f, 9f); arcTo(1f, 1f, 0f, false, false, 6.71f, 8.71f)
            lineTo(10.29f, 5.12f)
            arcTo(1.025f, 1.025f, 0f, false, true, 11.71f, 5.12f)
            lineTo(13.88f, 7.29f)
            arcTo(3f, 3f, 0f, false, false, 18.12f, 7.29f)
            lineTo(23.71f, 1.71f)
            arcTo(1f, 1f, 0f, false, false, 22.29f, 0.29f); lineTo(16.71f, 5.88f)
            arcTo(1f, 1f, 0f, false, true, 15.29f, 5.88f); lineTo(13.12f, 3.71f)
            arcTo(3f, 3f, 0f, false, false, 8.88f, 3.71f); lineTo(5.29f, 7.29f)
            arcTo(1f, 1f, 0f, false, false, 6f, 9f); close()
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

    // ── PR2c sweep constants (remaining screens) ──

    /** Flame (statistics streak). Source: `Icons.Outlined.LocalFireDepartment`. */
    val Flame = nextPageIcon("Flame") {
        strokePath {
            moveTo(12f, 3f); curveTo(15.5f, 7.5f, 18.5f, 10f, 18.5f, 14.5f); curveTo(18.5f, 18.5f, 15.6f, 21f, 12f, 21f)
            curveTo(8.4f, 21f, 5.5f, 18.5f, 5.5f, 14.5f); curveTo(5.5f, 10.5f, 8.8f, 7.5f, 12f, 3f)
            moveTo(12f, 21f); curveTo(9.8f, 21f, 8f, 19.5f, 8f, 17.3f); curveTo(8f, 15.3f, 9.5f, 13.8f, 11.5f, 12.8f)
            curveTo(11.8f, 15f, 13f, 16.5f, 14.8f, 16.5f); curveTo(14f, 19f, 13.2f, 21f, 12f, 21f)
        }
    }

    /** Chevron up. Source: `Icons.Filled.ExpandLess`. */
    val ChevronUp = nextPageIcon("ChevronUp") {
        strokePath {
            moveTo(6f, 15f); lineTo(12f, 9f); lineTo(18f, 15f)
        }
    }

    /** Chevron down. Source: `Icons.Filled.ExpandMore`. */
    val ChevronDown = nextPageIcon("ChevronDown") {
        strokePath {
            moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f)
        }
    }

    /** Envelope. Source: `Icons.Filled.Email`. */
    val Email = nextPageIcon("Email") {
        strokePath {
            moveTo(4f, 6f); lineTo(20f, 6f); lineTo(20f, 18f); lineTo(4f, 18f); lineTo(4f, 6f)
            moveTo(4f, 6f); lineTo(12f, 13f); lineTo(20f, 6f)
        }
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun NextPageIconsGalleryPreview() {
    val icons = listOf<Pair<String, ImageVector>>(
        "Home" to NextPageIcons.Home,
        "Library" to NextPageIcons.Library,
        "Highlights" to NextPageIcons.Highlights,
        "Settings" to NextPageIcons.Settings,
        "Statistics" to NextPageIcons.Statistics,
        "BookOpen" to NextPageIcons.BookOpen,
        "Book" to NextPageIcons.Book,
        "Person" to NextPageIcons.Person,
        "Search" to NextPageIcons.Search,
        "Close" to NextPageIcons.Close,
        "MoreVert" to NextPageIcons.MoreVert,
        "Add" to NextPageIcons.Add,
        "FilterList" to NextPageIcons.FilterList,
        "Notifications" to NextPageIcons.Notifications,
        "GridView" to NextPageIcons.GridView,
        "ViewList" to NextPageIcons.ViewList,
        "Clock" to NextPageIcons.Clock,
        "ChartLine" to NextPageIcons.ChartLine,
        "ChartBar" to NextPageIcons.ChartBar,
        "Bookmark" to NextPageIcons.Bookmark,
        "Upload" to NextPageIcons.Upload,
        "Sparkle" to NextPageIcons.Sparkle,
        "Quote" to NextPageIcons.Quote,
        "Lightbulb" to NextPageIcons.Lightbulb,
        "Check" to NextPageIcons.Check,
        "ErrorOutline" to NextPageIcons.ErrorOutline,
        "Trophy" to NextPageIcons.Trophy,
        "Palette" to NextPageIcons.Palette,
        "Copy" to NextPageIcons.Copy,
        "Tag" to NextPageIcons.Tag,
        "Pencil" to NextPageIcons.Pencil,
        "Share" to NextPageIcons.Share,
        "Trash" to NextPageIcons.Trash,
        "LibraryBooks" to NextPageIcons.LibraryBooks,
        "CloudDownload" to NextPageIcons.CloudDownload,
        "Devices" to NextPageIcons.Devices,
        "DarkMode" to NextPageIcons.DarkMode,
        "LightMode" to NextPageIcons.LightMode,
        "BrightnessAuto" to NextPageIcons.BrightnessAuto,
        "Language" to NextPageIcons.Language,
        "Storage" to NextPageIcons.Storage,
        "Info" to NextPageIcons.Info,
        "BugReport" to NextPageIcons.BugReport,
        "SignOut" to NextPageIcons.SignOut,
        "Smartphone" to NextPageIcons.Smartphone,
        "Monitor" to NextPageIcons.Monitor,
        "Laptop" to NextPageIcons.Laptop,
        "ChevronRight" to NextPageIcons.ChevronRight,
        "ArrowBack" to NextPageIcons.ArrowBack,
        "ArrowRight" to NextPageIcons.ArrowRight,
        "Sync" to NextPageIcons.Sync,
        "CloudSync" to NextPageIcons.CloudSync,
        "TextAa" to NextPageIcons.TextAa,
        "AlignLeft" to NextPageIcons.AlignLeft,
        "AlignCenter" to NextPageIcons.AlignCenter,
        "AlignRight" to NextPageIcons.AlignRight,
        "AlignJustify" to NextPageIcons.AlignJustify,
        "ArrowLeft" to NextPageIcons.ArrowLeft,
        "ArrowForward" to NextPageIcons.ArrowForward,
        "TextSize" to NextPageIcons.TextSize,
        "ListBullets" to NextPageIcons.ListBullets,
        "Star" to NextPageIcons.Star,
        "StarBorder" to NextPageIcons.StarBorder,
        "ScreenRotation" to NextPageIcons.ScreenRotation,
        "Flame" to NextPageIcons.Flame,
        "ChevronUp" to NextPageIcons.ChevronUp,
        "ChevronDown" to NextPageIcons.ChevronDown,
        "Email" to NextPageIcons.Email
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        icons.chunked(6).forEach { rowIcons ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowIcons.forEach { (name, icon) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color(0xFF000000)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
