package com.superglobalpeq.app.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superglobalpeq.app.R
import com.superglobalpeq.app.model.AppBypassCandidate
import com.superglobalpeq.app.model.BypassedAppItem
import com.superglobalpeq.app.service.PeqEngineManager
import com.superglobalpeq.app.ui.icons.LucideIcons
import com.superglobalpeq.app.ui.theme.LocalQuveExpressiveColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private object AppIconMemoryCache {
    private val cache = LruCache<String, ImageBitmap>(120)

    fun get(packageName: String): ImageBitmap? = cache.get(packageName)

    fun put(packageName: String, bmp: ImageBitmap) {
        cache.put(packageName, bmp)
    }
}

@Composable
fun AppIconBadge(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(32) }
    val cached = AppIconMemoryCache.get(packageName)
    val loadedBitmap by produceState<ImageBitmap?>(initialValue = cached, packageName, px) {
        if (value != null) return@produceState
        val rendered = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                if (drawable is BitmapDrawable && drawable.bitmap != null) {
                    val b = drawable.bitmap
                    if (b.width <= px * 2 && b.height <= px * 2) {
                        b.asImageBitmap()
                    } else {
                        Bitmap.createScaledBitmap(b, px, px, true).asImageBitmap()
                    }
                } else {
                    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, px, px)
                    drawable.draw(canvas)
                    bmp.asImageBitmap()
                }
            }.getOrNull()
        }
        if (rendered != null) {
            AppIconMemoryCache.put(packageName, rendered)
        }
        value = rendered
    }

    val shape = RoundedCornerShape(10.dp)
    if (loadedBitmap != null) {
        Image(
            bitmap = loadedBitmap!!,
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(shape)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = LucideIcons.ShieldOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}

@Composable
fun AppBypassSection(
    bypassedApps: List<BypassedAppItem>,
    onOpenPicker: () -> Unit,
    onRemoveApp: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ExpressiveGroupTitle(
            text = stringResource(R.string.routing_bypass_apps_title, bypassedApps.size)
        )
        Text(
            text = stringResource(R.string.routing_bypass_apps_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.height(2.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onOpenPicker,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(LucideIcons.Plus, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_select_bypass_apps),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (bypassedApps.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onClearAll,
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.action_clear_all),
                                fontWeight = FontWeight.SemiBold,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    }
                }

                if (bypassedApps.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = LucideIcons.ShieldOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.routing_bypass_apps_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    ExpressiveGroup {
                        bypassedApps.forEachIndexed { index, app ->
                            BypassedAppRow(
                                rowIndex = index,
                                rowCount = bypassedApps.size,
                                app = app,
                                onRemove = { onRemoveApp(app.packageName) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BypassedAppRow(
    rowIndex: Int,
    rowCount: Int,
    app: BypassedAppItem,
    onRemove: () -> Unit,
) {
    val rowColor = ExpressiveGroupDefaults.containerColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveItem(rowIndex, rowCount, rowColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AppIconBadge(packageName = app.packageName, size = 38.dp)

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = app.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (app.uid != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            text = stringResource(R.string.bypass_uid_badge, app.uid),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            softWrap = false,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            text = stringResource(R.string.bypass_not_installed),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            softWrap = false,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onRemove) {
            Icon(
                imageVector = LucideIcons.X,
                contentDescription = stringResource(R.string.cd_remove_bypass_app, app.displayName),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBypassPickerSheet(
    initialSelectedPackages: Set<String>,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit,
) {
    val expressiveColors = LocalQuveExpressiveColorScheme.current ?: MaterialTheme.colorScheme
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.80f).dp

    var candidates by remember { mutableStateOf<List<AppBypassCandidate>?>(null) }
    var draftSelected by remember(initialSelectedPackages) { mutableStateOf(initialSelectedPackages) }
    var searchQuery by remember { mutableStateOf("") }
    var filterTabIndex by remember { mutableIntStateOf(0) } // 0 = standard, 1 = all (+system)

    LaunchedEffect(Unit) {
        candidates = PeqEngineManager.loadBypassCandidateApps()
    }

    val displayedApps = remember(candidates, searchQuery, filterTabIndex, initialSelectedPackages) {
        val list = candidates ?: return@remember emptyList()
        val query = searchQuery.trim().lowercase(Locale.ROOT)
        list.asSequence()
            .filter { app ->
                val passesCategory = filterTabIndex == 1 ||
                    !app.isSystemApp ||
                    app.hasLauncherEntry ||
                    app.packageName in initialSelectedPackages
                val passesSearch = query.isEmpty() ||
                    app.label.lowercase(Locale.ROOT).contains(query) ||
                    app.packageName.lowercase(Locale.ROOT).contains(query)
                passesCategory && passesSearch
            }
            .sortedWith(
                compareByDescending<AppBypassCandidate> { it.packageName in initialSelectedPackages }
                    .thenBy { it.label.lowercase(Locale.ROOT) }
                    .thenBy { it.packageName }
            )
            .toList()
    }

    GlassModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .imePadding()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = expressiveColors.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = LucideIcons.ShieldOff,
                            contentDescription = null,
                            tint = expressiveColors.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.bypass_picker_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.bypass_picker_subtitle, draftSelected.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.bypass_picker_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    Icon(LucideIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                LucideIcons.X,
                                contentDescription = stringResource(R.string.cd_clear_search),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    null
                },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Standard vs All (+System) Filter
            ExpressiveSegmentBar(
                labels = listOf(
                    stringResource(R.string.bypass_filter_standard),
                    stringResource(R.string.bypass_filter_all)
                ),
                selectedIndex = filterTabIndex,
                onSelect = { filterTabIndex = it }
            )

            // App list
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(min = 180.dp)
            ) {
                when {
                    candidates == null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = expressiveColors.primary,
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = stringResource(R.string.bypass_picker_loading),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    displayedApps.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.bypass_picker_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(ExpressiveGroupDefaults.Gap)
                        ) {
                            itemsIndexed(
                                items = displayedApps,
                                key = { _, item -> item.packageName }
                            ) { index, app ->
                                val selected = app.packageName in draftSelected
                                val rowBg = if (selected) {
                                    expressiveColors.primaryContainer.copy(alpha = 0.48f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .expressiveItem(index, displayedApps.size, rowBg)
                                        .clickable {
                                            draftSelected = if (selected) {
                                                draftSelected - app.packageName
                                            } else {
                                                draftSelected + app.packageName
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AppIconBadge(packageName = app.packageName, size = 36.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = app.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                shape = CircleShape
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.bypass_uid_badge, app.uid),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    softWrap = false,
                                                    maxLines = 1,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = app.packageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    QuveExpressiveSwitch(
                                        checked = selected,
                                        onCheckedChange = { checked ->
                                            draftSelected = if (checked) {
                                                draftSelected + app.packageName
                                            } else {
                                                draftSelected - app.packageName
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Footer buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel), maxLines = 1, softWrap = false)
                }
                Button(
                    onClick = { onApply(draftSelected) },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = expressiveColors.primary,
                        contentColor = expressiveColors.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Icon(LucideIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_apply_count, draftSelected.size),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
