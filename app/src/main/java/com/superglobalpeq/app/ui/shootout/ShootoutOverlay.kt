package com.superglobalpeq.app.ui.shootout

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.superglobalpeq.app.MainActivity
import com.superglobalpeq.app.R
import com.superglobalpeq.app.data.QuveSettings
import com.superglobalpeq.app.dsp.ShootoutProcessor
import com.superglobalpeq.app.service.ShootoutManager
import com.superglobalpeq.app.ui.components.QuveGlyphMark
import com.superglobalpeq.app.ui.haptics.Haptic
import com.superglobalpeq.app.ui.haptics.rememberHaptics
import com.superglobalpeq.app.ui.icons.LucideIcons
import com.superglobalpeq.app.ui.theme.QuveTheme
import kotlin.math.roundToInt

/** The Material 3 Expressive vote panel drawn over the app that is playing music. */
object ShootoutOverlay {
    private const val TAG = "ShootoutOverlay"
    private var host: OverlayHost? = null

    fun show(context: Context) {
        if (host != null) return
        host = runCatching { OverlayHost(context).also { it.attach() } }
            .onFailure { Log.w(TAG, "Unable to show the Shootout panel", it) }
            .getOrNull()
    }

    fun hide() {
        host?.detach()
        host = null
    }
}

private class OverlayHost(context: Context) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val appContext = context.applicationContext
    private val windowContext = appContext.createWindowContext(
        checkNotNull(appContext.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        null,
    )
    private val windowManager = windowContext.getSystemService(WindowManager::class.java)
    private val view = ComposeView(QuveSettings.localizedContext(windowContext))
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "Quve Shootout"
    }
    private var attached = false

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore
        get() = store

    fun attach() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        val bounds = windowManager.currentWindowMetrics.bounds
        val density = view.resources.displayMetrics.density
        params.x = ((bounds.width() - PANEL_WIDTH_DP * density) / 2f).roundToInt().coerceAtLeast(0)
        params.y = (bounds.height() * 0.6f).roundToInt()
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setContent {
            QuveTheme {
                ShootoutOverlayPanel(
                    onMove = ::moveBy,
                    onSizeChanged = { moveBy(0f, 0f) },
                    onOpenQuve = ::openQuve,
                )
            }
        }
        windowManager.addView(view, params)
        attached = true
    }

    fun detach() {
        if (attached) {
            runCatching { windowManager.removeViewImmediate(view) }
            attached = false
        }
        view.disposeComposition()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }

    private fun moveBy(dx: Float, dy: Float) {
        if (!attached) return
        val bounds = windowManager.currentWindowMetrics.bounds
        params.x = (params.x + dx.roundToInt()).coerceIn(0, (bounds.width() - view.width).coerceAtLeast(0))
        params.y = (params.y + dy.roundToInt()).coerceIn(0, (bounds.height() - view.height).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun openQuve() {
        val intent = Intent(appContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { appContext.startActivity(intent) }
    }
}

private const val PANEL_WIDTH_DP = 340

@Composable
private fun ShootoutOverlayPanel(
    onMove: (Float, Float) -> Unit,
    onSizeChanged: () -> Unit,
    onOpenQuve: () -> Unit,
) {
    val state by ShootoutManager.state.collectAsState()
    val running = state.running ?: return
    val haptics = rememberHaptics()
    var collapsed by rememberSaveable { mutableStateOf(false) }
    val dragModifier = Modifier.pointerInput(Unit) {
        detectDragGestures { change, amount ->
            change.consume()
            onMove(amount.x, amount.y)
        }
    }
    val sideLabel = stringResource(
        if (running.side == ShootoutProcessor.SIDE_A) R.string.shootout_side_a else R.string.shootout_side_b
    )

    Box(
        modifier = Modifier
            .padding(8.dp)
            .onSizeChanged { onSizeChanged() },
    ) {
        if (collapsed) {
            val expandLabel = stringResource(R.string.shootout_panel_expand)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .size(60.dp)
                    .then(dragModifier)
                    .clickable(onClickLabel = expandLabel) {
                        haptics.play(Haptic.Expand)
                        collapsed = false
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = sideLabel, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 8.dp,
                modifier = Modifier.width((PANEL_WIDTH_DP - 16).dp),
            ) {
                Column(
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(dragModifier),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = LucideIcons.Trophy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 9.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.shootout_round_title, running.round),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(
                                    if (running.canVote) {
                                        if (running.side == ShootoutProcessor.SIDE_A) {
                                            R.string.shootout_playing_a
                                        } else {
                                            R.string.shootout_playing_b
                                        }
                                    } else {
                                        R.string.shootout_listen_both
                                    }
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val openLabel = stringResource(R.string.shootout_panel_open)
                        IconButton(
                            onClick = onOpenQuve,
                            modifier = Modifier.semantics { contentDescription = openLabel },
                        ) {
                            QuveGlyphMark(
                                modifier = Modifier.size(19.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(
                            onClick = {
                                haptics.play(Haptic.Tap)
                                collapsed = true
                            },
                        ) {
                            Icon(
                                imageVector = LucideIcons.ChevronDown,
                                contentDescription = stringResource(R.string.shootout_panel_collapse),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    ShootoutSideSelector(
                        side = running.side,
                        onSelect = ShootoutManager::selectSide,
                        height = 54.dp,
                        labelSize = 20.sp,
                    )
                    ShootoutVoteButtons(
                        enabled = running.canVote,
                        onVote = ShootoutManager::vote,
                        height = 46.dp,
                    )
                }
            }
        }
    }
}
