package de.crunchymate.ui.search

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SDK = 35
private const val ITEM_COUNT = 6
private val ITEM_HEIGHT = 40.dp

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w411dp-h891dp")
class AnimeGridCellsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun grid_alwaysHasThreeColumns() {
        rule.setContent {
            LazyVerticalGrid(columns = animeGridCells()) {
                items(ITEM_COUNT) { index -> Box(Modifier.size(ITEM_HEIGHT).testTag("cell-$index")) }
            }
        }
        val tops = (0 until ITEM_COUNT).map { rule.onNodeWithTag("cell-$it").getUnclippedBoundsInRoot().top }
        assertEquals(3, GRID_COLUMNS)
        assertTrue("erste Zeile hat drei Zellen", tops[0] == tops[1] && tops[1] == tops[2])
        assertTrue("vierte Zelle beginnt die zweite Zeile", tops[3] > tops[2])
    }
}
