package app.tide.launcher.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.tide.launcher.R
import app.tide.launcher.ui.theme.TideTheme
import app.tide.launcher.ui.theme.LocalOceanPalette
import app.tide.launcher.ui.theme.Radius
import app.tide.launcher.ui.theme.TideTypography

/**
 * First-run onboarding.
 *
 * Shown once, gated on the persisted `onboarded` flag, and dismissed for good by
 * [onDone]. Four steps: what Tide is, the three gestures that are not discoverable
 * by looking, a theme pick applied live rather than deferred to a save button,
 * and a ready state.
 *
 * It renders as an overlay above the live home screen rather than as its own
 * surface, so the user can see the real ocean and their real apps behind it while
 * being walked through — a mock screenshot would teach nothing about the actual
 * layout.
 */
@Composable
fun OnboardingFlow(
    onThemeChange: (TideTheme) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalOceanPalette.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var selectedTheme by remember { mutableStateOf(TideTheme.entries.first()) }

    // Keeps the last slide on screen during the exit animation rather than
    // snapping to a blank frame mid-dissolve.
    var exiting by remember { mutableStateOf(false) }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (exiting) 0f else 1f,
        animationSpec = tween(320),
        label = "onboardingScrim",
    )

    /**
     * Applies the chosen theme, then fades out before reporting completion.
     *
     * The flag is persisted *after* the exit animation rather than with it, so a
     * dismissal never blanks the screen for a frame if the write is slow.
     */
    fun requestDismiss() {
        if (exiting) return
        exiting = true
        onThemeChange(selectedTheme)
        scope.launch {
            delay(320)
            onDone()
        }
    }

    Box(modifier.fillMaxSize().testTag(TEST_TAG_ONBOARDING)) {
        // ── scrim ──────────────────────────────────────────────────────────
        // Declared first so it draws *behind* the content: in Compose, later
        // children are painted on top of earlier ones.
        //
        // A fixed deep ink rather than the theme's own gradient: the light
        // themes bottom out on a pale cyan, and a pale scrim over a pale
        // background leaves the app grid fully legible underneath the copy.
        Box(
            Modifier
                .fillMaxSize()
                // The home screen stays live behind this overlay, so without an
                // explicit consumer a tap on "Next" also lands on whatever app
                // sits underneath — and two taps in quick succession trip
                // double-tap-to-search while the flow is still on step one.
                //
                // This lives on the scrim, not the root: the content columns are
                // declared later, so they are hit-tested first and still get
                // their own taps, while anything landing here is swallowed
                // before it can reach the surface below.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                }
                .background(
                    Brush.verticalGradient(
                        0f to ScrimInk.copy(alpha = 0.88f * scrimAlpha),
                        1f to ScrimInk.copy(alpha = 0.96f * scrimAlpha),
                    ),
                ),
        )

        // ── page ───────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = !exiting,
                enter = fadeIn(tween(260)) + slideInVertically(tween(300)) { it / 8 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 8 },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    when (step) {
                        0 -> WelcomeStep()
                        1 -> GestureStep()
                        2 -> ThemeStep(
                            selected = selectedTheme,
                            onSelect = { selectedTheme = it },
                        )
                        else -> ReadyStep()
                    }
                }
            }
        }

        // ── controls ───────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = !exiting,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(260)),
            exit = fadeOut(tween(180)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Dots(count = STEP_COUNT, current = step)

                Spacer(Modifier.height(24.dp))

                GlassButton(
                    text = stringResource(
                        if (step == STEP_COUNT - 1) R.string.onboarding_start else R.string.onboarding_next,
                    ),
                    onClick = {
                        if (step == STEP_COUNT - 1) {
                            requestDismiss()
                        } else {
                            step++
                        }
                    },
                )

                if (step > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        style = TideTypography.labelLarge,
                        color = palette.onSurfaceMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .testTag(TEST_TAG_ONBOARDING_SKIP)
                            .clickable(onClick = { requestDismiss() })
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

const val TEST_TAG_ONBOARDING = "onboarding_root"
const val TEST_TAG_ONBOARDING_NEXT = "onboarding_next"
const val TEST_TAG_ONBOARDING_SKIP = "onboarding_skip"

private const val STEP_COUNT = 4

/** Deep water behind the copy, dark enough for white text in every theme. */
private val ScrimInk = Color(0xFF04141F)

@Composable
private fun WelcomeStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = TideTypography.displayMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_body),
            style = TideTypography.bodyLarge,
            color = Color.White.copy(alpha = 0.82f),
            textAlign = TextAlign.Center,
        )
    }
}

/** The gestures a first-time user cannot infer by looking at the screen. */
@Composable
private fun GestureStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.onboarding_gestures_title),
            style = TideTypography.headlineSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        GestureRow(
            icon = Icons.Rounded.Apps,
            title = stringResource(R.string.onboarding_gesture_longpress_title),
            body = stringResource(R.string.onboarding_gesture_longpress_body),
        )
        Spacer(Modifier.height(16.dp))
        GestureRow(
            icon = Icons.Rounded.SwapVert,
            title = stringResource(R.string.onboarding_gesture_drag_title),
            body = stringResource(R.string.onboarding_gesture_drag_body),
        )
        Spacer(Modifier.height(16.dp))
        GestureRow(
            icon = Icons.Rounded.Search,
            title = stringResource(R.string.onboarding_gesture_drawer_title),
            body = stringResource(R.string.onboarding_gesture_drawer_body),
        )
    }
}

@Composable
private fun GestureRow(icon: ImageVector, title: String, body: String) {
    val palette = LocalOceanPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(palette.accent.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = palette.accent)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = TideTypography.titleSmall, color = Color.White)
            Text(
                body,
                style = TideTypography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
            )
        }
    }
}

/** Applies live so the choice can be judged against the real background. */
@Composable
private fun ThemeStep(selected: TideTheme, onSelect: (TideTheme) -> Unit) {
    val palette = LocalOceanPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.onboarding_theme_title),
            style = TideTypography.headlineSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_theme_body),
            style = TideTypography.bodyMedium,
            color = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TideTheme.entries.forEach { theme ->
                val isSelected = theme == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.md))
                        .background(
                            if (isSelected) palette.accent.copy(alpha = 0.22f)
                            else Color.White.copy(alpha = 0.08f),
                        )
                        .clickable { onSelect(theme) }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = theme.displayName,
                        style = TideTypography.titleSmall,
                        color = if (isSelected) palette.accent else Color.White,
                    )
                    if (isSelected) {
                        Spacer(Modifier.height(4.dp))
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyStep() {
    val palette = LocalOceanPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(palette.accent.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_ready_title),
            style = TideTypography.headlineSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_ready_body),
            style = TideTypography.bodyLarge,
            color = Color.White.copy(alpha = 0.78f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Dots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(if (index == current) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == current) Color.White
                        else Color.White.copy(alpha = 0.35f),
                    ),
            )
        }
    }
}

@Composable
private fun GlassButton(text: String, onClick: () -> Unit) {
    val palette = LocalOceanPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.pill))
            .background(
                Brush.horizontalGradient(
                    listOf(palette.accent, palette.accent.copy(alpha = 0.82f)),
                ),
            )
            .testTag(TEST_TAG_ONBOARDING_NEXT)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TideTypography.titleMedium, color = Color.White)
    }
}