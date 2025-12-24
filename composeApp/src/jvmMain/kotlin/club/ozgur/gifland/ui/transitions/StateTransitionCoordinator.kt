package club.ozgur.gifland.ui.transitions

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import club.ozgur.gifland.domain.model.AppState
import kotlinx.coroutines.delay

/**
 * Coordinates state transitions with smooth animations and proper sequencing.
 * Ensures UI states transition smoothly without jarring changes.
 */
class StateTransitionCoordinator {

    companion object {
        // Animation specs for different transition types
        val quickTransition = tween<Float>(200, easing = FastOutSlowInEasing)
        val normalTransition = tween<Float>(300, easing = FastOutSlowInEasing)
        val smoothTransition = tween<Float>(500, easing = FastOutSlowInEasing)

        // Slide animations
        val slideInFromBottom = slideInVertically(
            animationSpec = tween(300),
            initialOffsetY = { it }
        )

        val slideOutToBottom = slideOutVertically(
            animationSpec = tween(300),
            targetOffsetY = { it }
        )

        val slideInFromRight = slideInHorizontally(
            animationSpec = tween(300),
            initialOffsetX = { it }
        )

        val slideOutToLeft = slideOutHorizontally(
            animationSpec = tween(300),
            targetOffsetX = { -it }
        )

        // Fade animations
        val fadeIn = fadeIn(
            animationSpec = tween(200)
        )

        val fadeOut = fadeOut(
            animationSpec = tween(200)
        )

        // Scale animations
        val scaleIn = scaleIn(
            animationSpec = tween(300),
            initialScale = 0.8f
        )

        val scaleOut = scaleOut(
            animationSpec = tween(300),
            targetScale = 0.8f
        )
    }

    /**
     * Get the appropriate enter transition for a state
     */
    fun getEnterTransition(state: AppState): EnterTransition {
        return when (state) {
            is AppState.Initializing -> fadeIn
            is AppState.Idle -> fadeIn
            is AppState.PreparingRecording -> fadeIn + slideInFromBottom
            is AppState.Recording -> scaleIn + fadeIn
            is AppState.Processing -> fadeIn
            is AppState.Editing -> slideInFromRight + fadeIn
            is AppState.ConfiguringSettings -> slideInFromRight + fadeIn
            is AppState.Error -> fadeIn + scaleIn
        }
    }

    /**
     * Get the appropriate exit transition for a state
     */
    fun getExitTransition(state: AppState): ExitTransition {
        return when (state) {
            is AppState.Initializing -> fadeOut
            is AppState.Idle -> fadeOut
            is AppState.PreparingRecording -> fadeOut + slideOutToBottom
            is AppState.Recording -> scaleOut + fadeOut
            is AppState.Processing -> fadeOut
            is AppState.Editing -> slideOutToLeft + fadeOut
            is AppState.ConfiguringSettings -> slideOutToLeft + fadeOut
            is AppState.Error -> fadeOut + scaleOut
        }
    }

    /**
     * Check if a transition between states should be animated
     */
    fun shouldAnimate(from: AppState, to: AppState): Boolean {
        // Don't animate between similar states
        if (from::class == to::class) return false

        // Always animate recording state changes
        if (from is AppState.Recording || to is AppState.Recording) return true

        // Always animate error states
        if (from is AppState.Error || to is AppState.Error) return true

        // Animate settings transitions
        if (from is AppState.ConfiguringSettings || to is AppState.ConfiguringSettings) return true

        return true
    }

    /**
     * Get transition delay between states (if needed)
     */
    fun getTransitionDelay(from: AppState, to: AppState): Long {
        return when {
            // Quick transition from idle to recording preparation
            from is AppState.Idle && to is AppState.PreparingRecording -> 0L

            // Small delay when starting recording to show countdown
            from is AppState.PreparingRecording && to is AppState.Recording -> 100L

            // Delay after processing to show success
            from is AppState.Processing && to is AppState.Idle -> 500L

            // Quick transition for errors
            to is AppState.Error -> 0L

            // Default no delay
            else -> 0L
        }
    }
}

/**
 * Composable wrapper for animated state transitions
 */
@Composable
fun AnimatedStateTransition(
    state: AppState,
    coordinator: StateTransitionCoordinator = remember { StateTransitionCoordinator() },
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = true,
        enter = coordinator.getEnterTransition(state),
        exit = coordinator.getExitTransition(state),
        content = content
    )
}

/**
 * Crossfade between different state contents
 */
@Composable
fun CrossfadeState(
    state: AppState,
    animationSpec: FiniteAnimationSpec<Float> = tween(300),
    content: @Composable (AppState) -> Unit
) {
    Crossfade(
        targetState = state,
        animationSpec = animationSpec
    ) { targetState ->
        content(targetState)
    }
}

/**
 * Animated content for state changes with custom transitions
 */
@ExperimentalAnimationApi
@Composable
fun AnimatedStateContent(
    state: AppState,
    coordinator: StateTransitionCoordinator = remember { StateTransitionCoordinator() },
    content: @Composable AnimatedVisibilityScope.(AppState) -> Unit
) {
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            val enter = coordinator.getEnterTransition(targetState)
            val exit = coordinator.getExitTransition(initialState)

            if (coordinator.shouldAnimate(initialState, targetState)) {
                enter togetherWith exit
            } else {
                EnterTransition.None togetherWith ExitTransition.None
            }
        }
    ) { targetState ->
        content(targetState)
    }
}

/**
 * State-aware animated visibility
 */
@Composable
fun StateVisibility(
    visible: Boolean,
    state: AppState,
    coordinator: StateTransitionCoordinator = remember { StateTransitionCoordinator() },
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = coordinator.getEnterTransition(state),
        exit = coordinator.getExitTransition(state),
        content = content
    )
}

/**
 * Recording state specific animations
 */
@Composable
fun RecordingStateAnimation(
    isRecording: Boolean,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = when {
            isPaused -> 0.5f
            isRecording -> 1f
            else -> 0.8f
        },
        animationSpec = tween(200)
    )

    val scale by animateFloatAsState(
        targetValue = when {
            isPaused -> 0.95f
            isRecording -> 1f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Box(
        modifier = modifier
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                alpha = alpha
            )
    ) {
        content()
    }
}

/**
 * Pulsating animation for recording indicator
 */
@Composable
fun RecordingIndicatorAnimation(
    isRecording: Boolean
): State<Float> {
    val infiniteTransition = rememberInfiniteTransition()

    return if (isRecording) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse
            )
        )
    } else {
        remember { mutableStateOf(1f) }
    }
}

/**
 * Progress animation for saving state
 */
@Composable
fun SavingProgressAnimation(
    progress: Float,
    content: @Composable (animatedProgress: Float) -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(
            durationMillis = 300,
            easing = LinearEasing
        )
    )

    content(animatedProgress)
}

/**
 * Error shake animation
 */
@Composable
fun ErrorShakeAnimation(
    trigger: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val offsetX = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        if (trigger) {
            offsetX.animateTo(
                targetValue = with(density) { 10.dp.toPx() },
                animationSpec = tween(50)
            )
            offsetX.animateTo(
                targetValue = with(density) { -10.dp.toPx() },
                animationSpec = tween(100)
            )
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = tween(50)
            )
        }
    }

    Box(
        modifier = modifier.offset(x = offsetX.value.dp)
    ) {
        content()
    }
}

/**
 * Success check animation
 */
@Composable
fun SuccessCheckAnimation(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(),
        exit = scaleOut() + fadeOut()
    ) {
        content()
    }
}