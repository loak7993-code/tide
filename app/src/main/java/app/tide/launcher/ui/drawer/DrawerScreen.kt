package app.tide.launcher.ui.drawer

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import app.tide.launcher.R
import app.tide.launcher.data.AppEntry
import app.tide.launcher.data.Fuzzy
import app.tide.launcher.data.IconShape
import app.tide.launcher.ui.components.AppIconTile
import app.tide.launcher.ui.components.CenteredHint
import app.tide.launcher.ui.components.GlassPill
import app.tide.launcher.ui.theme.LocalOceanPalette
import app.tide.launcher.ui.theme.TideTypography

/** One row of the flattened drawer list. */
private sealed interface DrawerItem {
    data class Header(val letter: Char) : DrawerItem
    data class App(val entry: AppEntry) : DrawerItem
    data object Empty : DrawerItem
}

private const val TEST_TAG_SEARCH = "drawer_search"
private const val TEST_TAG_GRID = "drawer_grid"

/**
 * The full app list, with search and an alphabetical rail.
 *
 * When the query is blank the list is grouped under letter headers; when it is
 * not, the headers are dropped and the fuzzy ranking is shown flat. That is the
 * behaviour people expect from a launcher: type to search, clear to browse.
 */
@Composable
fun DrawerScreen(
    apps: List<AppEntry>,
    query: String,
    iconShape: IconShape,
    showLabels: Boolean,
    columns: Int,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onQueryChange: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalOceanPalette.current
    val gridState = rememberLazyGridState()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    val searching = query.isNotBlank()
    val results = remember(apps, query) {
        if (searching) Fuzzy.rank(apps, query) else apps
    }

    // Flatten once per result change; the rail indexes into the same list the
    // grid renders, so its offsets line up with what the user sees.
    val items = remember(results, searching) { flatten(results, searching) }
    val rail = remember(items) {
        items.withIndex()
            .filter { (_, item) -> item is DrawerItem.Header }
            .map { (index, item) -> (item as DrawerItem.Header).letter to index }
    }

    // Opening the drawer with an empty query should be ready to type.
    LaunchedEffect(Unit) {
        if (query.isBlank()) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Box(modifier.fillMaxSize().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            // ── search bar ───────────────────────────────────────────────────
            SearchField(
                query = query,
                onQueryChange = {
                    onQueryChange(it)
                    if (it.isNotEmpty()) scope.launch { gridState.animateScrollToItem(0) }
                },
                onClose = {
                    onQueryChange("")
                    keyboard?.hide()
                    onDismiss()
                },
                focusRequester = focusRequester,
                modifier = Modifier
                    .fillMaxWidth()
                    // Only the top inset applies here. Pushing the full
                    // `contentPadding` onto the field would add the navigation
                    // bar height as padding underneath it, pushing the whole
                    // list down by an extra bar's worth.
                    .padding(top = contentPadding.calculateTopPadding())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )

            Box(Modifier.weight(1f)) {
                if (results.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CenteredHint(
                            text = if (searching) {
                                stringResource(R.string.no_results, query)
                            } else {
                                stringResource(R.string.no_apps)
                            },
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(columns),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 12.dp,
                            end = 34.dp,
                            bottom = contentPadding.calculateBottomPadding() + 16.dp,
                            top = 4.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        // Alignment pinned explicitly: `spacedBy` alone centres the
                        // content vertically, so a short result list floats in the
                        // middle of an empty screen instead of sitting under the
                        // search field.
                        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Top),
                        modifier = Modifier.fillMaxSize().testTag(TEST_TAG_GRID),
                    ) {
                        items.forEach { item ->
                            when (item) {
                                is DrawerItem.Header -> {
                                    // Full line span: a header sitting in one
                                    // grid cell scatters down the page instead of
                                    // running across the top of its section.
                                    item(
                                        key = "header_${item.letter}",
                                        span = { GridItemSpan(maxLineSpan) },
                                    ) {
                                        LetterHeader(item.letter)
                                    }
                                }

                                is DrawerItem.App -> {
                                    item(key = item.entry.key) {
                                        AppIconTile(
                                            entry = item.entry,
                                            shape = iconShape,
                                            showLabel = showLabels,
                                            onClick = {
                                                keyboard?.hide()
                                                onLaunch(item.entry)
                                            },
                                            onLongClick = { onLongPress(item.entry) },
                                        )
                                    }
                                }

                                DrawerItem.Empty -> item(key = "empty") {
                                    Spacer(Modifier.height(1.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── alphabetical rail ────────────────────────────────────────────────
        if (rail.size > 1) {
            AlphabetRail(
                entries = rail,
                onPick = { index -> scope.launch { gridState.animateScrollToItem(index) } },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(
                        top = contentPadding.calculateTopPadding() + 70.dp,
                        bottom = contentPadding.calculateBottomPadding() + 16.dp,
                    )
                    .padding(end = 6.dp),
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val palette = LocalOceanPalette.current
    val interactionSource = remember { MutableInteractionSource() }

    GlassPill(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = palette.onSurfaceMuted,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(
                    TideTypography.bodyLarge.copy(color = palette.onGlass),
                ),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(),
                interactionSource = interactionSource,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp)
                    .focusRequester(focusRequester)
                    .testTag(TEST_TAG_SEARCH),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_apps),
                                style = TideTypography.bodyLarge,
                                color = palette.onSurfaceMuted,
                            )
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(palette.glassBorder.copy(alpha = 0.18f))
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = palette.onGlass,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun LetterHeader(letter: Char) {
    val palette = LocalOceanPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 2.dp, start = 4.dp),
    ) {
        Text(
            text = letter.toString(),
            style = TideTypography.labelLarge,
            color = palette.onSurfaceMuted,
        )
    }
}

/**
 * The A–Z rail. Each letter tints toward the accent while the grid is scrolled
 * near its section, which gives the rail a sense of position without a
 * scrollbar.
 */
@Composable
private fun AlphabetRail(
    entries: List<Pair<Char, Int>>,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalOceanPalette.current

    Column(
        modifier = modifier
            .width(24.dp)
            .clip(CircleShape)
            .background(palette.glass.copy(alpha = palette.glass.alpha * 1.4f))
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        entries.forEach { (letter, index) ->
            val interactionSource = remember { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            val color by animateColorAsState(
                targetValue = if (pressed) palette.accent else palette.onSurfaceMuted,
                label = "railColor",
            )
            Text(
                text = letter.toString(),
                style = TideTypography.labelSmall,
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onPick(index) },
                    )
                    .padding(vertical = 1.dp),
            )
        }
    }
}

/** Groups results under letter headers, or returns them flat while searching. */
private fun flatten(apps: List<AppEntry>, searching: Boolean): List<DrawerItem> {
    if (searching) return apps.map { DrawerItem.App(it) }
    if (apps.isEmpty()) return listOf(DrawerItem.Empty)

    val grouped = apps.groupBy { Fuzzy.alphabetOf(it.label) }
    return buildList {
        grouped.keys.sorted().forEach { letter ->
            add(DrawerItem.Header(letter))
            grouped.getValue(letter).forEach { add(DrawerItem.App(it)) }
        }
    }
}