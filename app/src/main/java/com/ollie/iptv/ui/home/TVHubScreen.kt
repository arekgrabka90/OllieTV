package com.ollie.iptv.ui.home

import android.content.Context
import coil.compose.AsyncImage
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ollie.iptv.data.*
import com.ollie.iptv.ui.LiveSortMode
import com.ollie.iptv.ui.theme.Muted
import com.ollie.iptv.ui.theme.Panel

private data class LiveCategoryOption(
    val key: String,
    val name: String,
    val playlistId: Long,
    val playlistName: String,
    val count: Int
)

private fun normalizedLiveCategory(value: String): String {
    val clean = value.trim()
    return if (
        clean.isBlank() ||
        clean.equals("All channels", ignoreCase = true)
    ) "Uncategorized" else clean
}

@Composable
fun TVHubScreen(
    channels: List<StreamEntity>,
    allChannels: List<StreamEntity>,
    recentChannels: List<StreamEntity>,
    favorites: List<StreamEntity>,
    playlists: List<PlaylistEntity>,
    selectedCategory: String,
    selectedChannel: StreamEntity?,
    search: String,
    sortMode: LiveSortMode,
    programmes: List<ProgrammeEntity>,
    channelMappings: List<ChannelMappingEntity>,
    lockedChannelIds: Set<String>,
    expandedLayout: Boolean,
    compactLayout: Boolean = false,
    onSearchChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onSortChange: (LiveSortMode) -> Unit,
    onChannelClick: (StreamEntity) -> Unit,
    onFavorite: (StreamEntity) -> Unit,
    onLockToggle: (StreamEntity) -> Unit,
    onAddToGroup: (StreamEntity) -> Unit,
    showLogos: Boolean = true,
    showCategory: Boolean = true,
    showRecentChannels: Boolean = true,
    compactRows: Boolean = false,
    guideContent: @Composable () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("ollie_live_hub", Context.MODE_PRIVATE)
    }

    var hubMode by rememberSaveable { mutableStateOf("CHANNELS") }
    var quickFilter by rememberSaveable { mutableStateOf("ALL") }
    var categoryDialog by rememberSaveable { mutableStateOf(false) }
    var categoryQuery by rememberSaveable { mutableStateOf("") }
    var categorySortByCount by rememberSaveable { mutableStateOf(true) }

    var pinnedCategories by remember {
        mutableStateOf(
            preferences.getStringSet("pinned_categories", emptySet())
                ?.toSet() ?: emptySet()
        )
    }

    var lastCategory by remember {
        mutableStateOf(
            preferences.getString("last_category", "All channels")
                ?: "All channels"
        )
    }

    val playlistNames = remember(playlists) {
        playlists.associate { it.id to it.name }
    }

    val categoryOptions = remember(allChannels, playlistNames) {
        allChannels
            .groupBy {
                it.playlistId to normalizedLiveCategory(it.category)
            }
            .map { (key, items) ->
                val playlistId = key.first
                val categoryName = key.second
                LiveCategoryOption(
                    key = "playlist:$playlistId::$categoryName",
                    name = categoryName,
                    playlistId = playlistId,
                    playlistName = playlistNames[playlistId]
                        ?: "Playlist $playlistId",
                    count = items.size
                )
            }
    }

    val selectedCategoryName = when {
        selectedCategory == "All channels" -> "All channels"
        else -> categoryOptions.firstOrNull {
            it.key == selectedCategory
        }?.let {
            "${it.name} · ${it.playlistName}"
        } ?: selectedCategory
    }

    val visibleChannels = when (quickFilter) {
        "FAVORITES" -> {
            val favoriteIds = favorites.mapTo(mutableSetOf()) { it.id }
            channels.filter { it.id in favoriteIds || it.isFavorite }
        }
        "RECENT" -> {
            val visibleIds = channels.mapTo(mutableSetOf()) { it.id }
            recentChannels.filter { it.id in visibleIds }
        }
        else -> channels
    }

    val previewChannel = selectedChannel?.takeIf { selected ->
        visibleChannels.any { it.id == selected.id }
    } ?: visibleChannels.firstOrNull()

    val mappingByStream = remember(channelMappings) {
        channelMappings.associateBy { it.streamId }
    }

    val programmesByChannel = remember(programmes) {
        programmes.groupBy { it.channelId }
    }

    fun programmesFor(channel: StreamEntity): List<ProgrammeEntity> {
        val epgId = mappingByStream[channel.id]
            ?.xmltvChannelId
            ?.takeIf { it.isNotBlank() }
            ?: channel.tvgId

        if (epgId.isBlank()) return emptyList()

        return programmesByChannel[epgId]
            .orEmpty()
            .sortedBy { it.startUtc }
    }

    fun selectCategory(key: String) {
        onCategoryChange(key)
        lastCategory = key
        preferences.edit().putString("last_category", key).apply()
        categoryDialog = false
        categoryQuery = ""
        quickFilter = "ALL"
    }

    fun togglePinned(key: String) {
        val updated = pinnedCategories.toMutableSet()
        if (key in updated) updated.remove(key) else updated.add(key)
        pinnedCategories = updated
        preferences.edit()
            .putStringSet("pinned_categories", updated)
            .apply()
    }

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (compactLayout) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Live TV", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        buildString {
                            append("${visibleChannels.size} channels")
                            if (selectedCategory != "All channels") {
                                append(" · ")
                                append(selectedCategoryName)
                            }
                        },
                        color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "LIVE HUB",
                    color = Color(0xFFB8D8FF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    "Live television",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${allChannels.size} channels · ${playlists.size} playlists",
                    color = Muted,
                    fontSize = 11.sp
                )
            }

            Surface(
                color = Color(0xFF202D3A),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.LiveTv,
                        null,
                        tint = Color(0xFFB8D8FF),
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        "${visibleChannels.size}",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }
        }


        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = hubMode == "CHANNELS",
                onClick = { hubMode = "CHANNELS" },
                label = { Text("Channels") },
                leadingIcon = {
                    Icon(Icons.Default.Tv, null, Modifier.size(16.dp))
                }
            )
            FilterChip(
                selected = hubMode == "GUIDE",
                onClick = { hubMode = "GUIDE" },
                label = { Text("TV Guide") },
                leadingIcon = {
                    Icon(Icons.Default.CalendarMonth, null, Modifier.size(16.dp))
                }
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = onSearchChange,
                modifier = Modifier.weight(1f).heightIn(max = 58.dp),
                placeholder = {
                    Text(if (hubMode == "GUIDE") "Search channels or programmes" else "Search channels or IDs")
                },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (search.isNotBlank()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, "Clear search")
                        }
                    }
                },
                singleLine = true
            )

            if (hubMode == "CHANNELS") {
                OutlinedButton(
                    onClick = { categoryDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, null)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (selectedCategory == "All channels") "Category" else "Filtered",
                        maxLines = 1
                    )
                }
            }
        }

        if (hubMode == "CHANNELS") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = quickFilter == "ALL",
                    onClick = { quickFilter = "ALL" },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = quickFilter == "FAVORITES",
                    onClick = { quickFilter = "FAVORITES" },
                    label = { Text("Favorites") },
                    leadingIcon = {
                        Icon(Icons.Default.Star, null, Modifier.size(15.dp))
                    }
                )
                FilterChip(
                    selected = quickFilter == "RECENT",
                    onClick = { quickFilter = "RECENT" },
                    label = { Text("Recent") },
                    leadingIcon = {
                        Icon(Icons.Default.History, null, Modifier.size(15.dp))
                    }
                )

                Spacer(Modifier.weight(1f))

                var sortExpanded by remember { mutableStateOf(false) }

                Box {
                    IconButton(onClick = { sortExpanded = true }) {
                        Icon(Icons.Default.Sort, "Sort channels", tint = Muted)
                    }

                    DropdownMenu(
                        expanded = sortExpanded,
                        onDismissRequest = { sortExpanded = false }
                    ) {
                        listOf(
                            LiveSortMode.SOURCE to "Playlist order",
                            LiveSortMode.NAME_ASC to "Name A–Z",
                            LiveSortMode.NAME_DESC to "Name Z–A",
                            LiveSortMode.CATEGORY to "Category"
                        ).forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                leadingIcon = {
                                    if (mode == sortMode) {
                                        Icon(Icons.Default.Check, null)
                                    }
                                },
                                onClick = {
                                    onSortChange(mode)
                                    sortExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (!compactLayout) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Surface(
                    onClick = { categoryDialog = true },
                    color = Color(0xFF202D3A),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        Color(0xFFB8D8FF).copy(alpha = .20f)
                    )
                ) {
                    Row(
                        Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            null,
                            tint = Color(0xFFB8D8FF),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            selectedCategoryName,
                            color = Color.White,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            Icons.Default.ExpandMore,
                            null,
                            tint = Muted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (lastCategory != "All channels" &&
                    categoryOptions.any { it.key == lastCategory } &&
                    lastCategory != selectedCategory
                ) {
                    TextButton(
                        onClick = { selectCategory(lastCategory) },
                        contentPadding = PaddingValues(horizontal = 5.dp)
                    ) {
                        Icon(Icons.Default.History, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Last category", fontSize = 10.sp)
                    }
                }
            }


            }

            if (!compactLayout) {
            val pinnedOptions = categoryOptions
                .filter { it.key in pinnedCategories }
                .take(8)

            if (pinnedOptions.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(pinnedOptions, key = { it.key }) { option ->
                        FilterChip(
                            selected = selectedCategory == option.key,
                            onClick = { selectCategory(option.key) },
                            label = {
                                Text(option.name, maxLines = 1)
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.PushPin,
                                    null,
                                    Modifier.size(13.dp)
                                )
                            }
                        )
                    }
                }
            }


            }

            if (!compactLayout && showRecentChannels && recentChannels.isNotEmpty() &&
                quickFilter != "RECENT"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        "RECENT CHANNELS",
                        color = Muted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(recentChannels, key = { it.id }) { channel ->
                            Surface(
                                onClick = { onChannelClick(channel) },
                                color = Panel,
                                shape = RoundedCornerShape(13.dp),
                                border = BorderStroke(
                                    1.dp,
                                    Color.White.copy(alpha = .07f)
                                ),
                                modifier = Modifier
                                    .widthIn(min = 115.dp, max = 185.dp)
                                    .height(43.dp)
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (showLogos && channel.logo.isNotBlank()) {
                                        AsyncImage(
                                            model = channel.logo,
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.size(23.dp)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Tv,
                                            null,
                                            tint = Muted,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(7.dp))
                                    Text(
                                        channel.name,
                                        maxLines = 1,
                                        fontSize = 10.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (visibleChannels.isEmpty()) {
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SearchOff,
                            null,
                            tint = Muted,
                            modifier = Modifier.size(35.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("No channels found", color = Color.White)
                        Text(
                            "Try another search or category.",
                            color = Muted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else if (expandedLayout) {
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LazyColumn(
                        Modifier.weight(.58f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(
                            visibleChannels,
                            key = { it.id },
                            contentType = { "live-channel" }
                        ) { channel ->
                            LiveChannelRow(
                                channel = channel,
                                selected = previewChannel?.id == channel.id,
                                locked = channel.id in lockedChannelIds,
                                showLogos = showLogos,
                                showCategory = showCategory,
                                compactRows = compactRows || compactLayout
                                programmes = programmesFor(channel),
                                onClick = { onChannelClick(channel) },
                                onFavorite = { onFavorite(channel) },
                                onLock = { onLockToggle(channel) },
                                onAddToGroup = { onAddToGroup(channel) }
                            )
                        }
                    }

                    VerticalDivider(color = Color(0xFF303943))

                    LiveChannelDetails(
                        channel = previewChannel,
                        programmes = previewChannel?.let(::programmesFor).orEmpty(),
                        locked = previewChannel?.id?.let { it in lockedChannelIds } == true,
                        showLogos = showLogos,
                        onPlay = {
                            previewChannel?.let(onChannelClick)
                        },
                        modifier = Modifier.weight(.42f).fillMaxHeight()
                    )
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(
                        visibleChannels,
                        key = { it.id },
                        contentType = { "live-channel" }
                    ) { channel ->
                        LiveChannelRow(
                            channel = channel,
                            selected = previewChannel?.id == channel.id,
                            locked = channel.id in lockedChannelIds,
                            showLogos = showLogos,
                            showCategory = showCategory,
                            compactRows = compactRows || compactLayout
                            programmes = programmesFor(channel),
                            onClick = { onChannelClick(channel) },
                            onFavorite = { onFavorite(channel) },
                            onLock = { onLockToggle(channel) },
                            onAddToGroup = { onAddToGroup(channel) }
                        )
                    }
                }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                guideContent()
            }
        }
    }

    if (categoryDialog) {
        val sortedOptions = categoryOptions.sortedWith(
            compareBy<LiveCategoryOption>(
                { if (it.key in pinnedCategories) 0 else 1 },
                { if (categorySortByCount) -it.count else 0 },
                { it.playlistName.lowercase() },
                { it.name.lowercase() }
            )
        )

        val filteredOptions = sortedOptions.filter {
            categoryQuery.isBlank() ||
                it.name.contains(categoryQuery, ignoreCase = true) ||
                it.playlistName.contains(categoryQuery, ignoreCase = true)
        }

        Dialog(onDismissRequest = { categoryDialog = false }) {
            Surface(
                Modifier.fillMaxWidth().heightIn(min = 360.dp, max = 640.dp),
                color = Color(0xFF151C25),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .12f))
            ) {
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Choose category",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            )
                            Text(
                                "${categoryOptions.size} categories across ${playlists.size} playlists",
                                color = Muted,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(onClick = { categoryDialog = false }) {
                            Icon(Icons.Default.Close, "Close", tint = Muted)
                        }
                    }

                    OutlinedTextField(
                        value = categoryQuery,
                        onValueChange = { categoryQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search categories or playlists") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Sort categories",
                            color = Muted,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                categorySortByCount = !categorySortByCount
                            }
                        ) {
                            Icon(Icons.Default.Sort, null, Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (categorySortByCount) "Most channels"
                                else "A–Z"
                            )
                        }
                    }

                    Surface(
                        onClick = { selectCategory("All channels") },
                        color = if (selectedCategory == "All channels")
                            Color(0xFF29415A) else Color(0xFF202D3A),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Tv, null, tint = Color(0xFFB8D8FF))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "All channels",
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${allChannels.size}",
                                color = Muted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    LazyColumn(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(filteredOptions, key = { it.key }) { option ->
                            Surface(
                                onClick = { selectCategory(option.key) },
                                color = if (selectedCategory == option.key)
                                    Color(0xFF29415A)
                                else Color(0xFF202D3A),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(
                                        start = 11.dp,
                                        end = 5.dp,
                                        top = 7.dp,
                                        bottom = 7.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (selectedCategory == option.key)
                                            Icons.Default.CheckCircle
                                        else Icons.Default.Folder,
                                        null,
                                        tint = if (selectedCategory == option.key)
                                            Color(0xFFB8D8FF) else Muted
                                    )
                                    Spacer(Modifier.width(9.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            option.name,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            option.playlistName,
                                            color = Muted,
                                            fontSize = 10.sp,
                                            maxLines = 1
                                        )
                                    }
                                    Text(
                                        "${option.count}",
                                        color = Muted,
                                        fontSize = 11.sp
                                    )
                                    IconButton(
                                        onClick = { togglePinned(option.key) }
                                    ) {
                                        Icon(
                                            if (option.key in pinnedCategories)
                                                Icons.Default.PushPin
                                            else Icons.Default.PushPin,
                                            if (option.key in pinnedCategories)
                                                "Unpin category"
                                            else "Pin category",
                                            tint = if (option.key in pinnedCategories)
                                                Color(0xFFB8D8FF) else Muted
                                        )
                                    }
                                }
                            }
                        }

                        if (filteredOptions.isEmpty()) {
                            item {
                                Text(
                                    "No matching categories.",
                                    color = Muted,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = { selectCategory("All channels") },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Clear category filter")
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveChannelRow(
    channel: StreamEntity,
    selected: Boolean,
    locked: Boolean,
    showLogos: Boolean,
    showCategory: Boolean,
    compactRows: Boolean,
    programmes: List<ProgrammeEntity>,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onLock: () -> Unit,
    onAddToGroup: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    var menuExpanded by remember(channel.id) { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val current = programmes.firstOrNull {
        now >= it.startUtc && now < it.endUtc
    }

    Surface(
        onClick = onClick,
        color = when {
            selected -> Color(0xE626394B)
            focused -> Color(0xD9212D39)
            else -> Panel
        },
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(
            1.dp,
            when {
                selected -> Color(0xFFB8D8FF).copy(alpha = .42f)
                focused -> Color(0xFFB8D8FF).copy(alpha = .30f)
                else -> Color.White.copy(alpha = .07f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
    ) {
        Row(
            Modifier.padding(
                horizontal = 10.dp,
                vertical = if (compactRows) 5.dp else 8.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showLogos && channel.logo.isNotBlank()) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = "${channel.name} logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(if (compactRows) 32.dp else 42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF252D36))
                        .padding(3.dp)
                )
            } else {
                Box(
                    Modifier
                        .size(if (compactRows) 32.dp else 42.dp)
                        .background(Color(0xFF252D36), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Tv, null, tint = Muted)
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    channel.name,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (current != null) {
                    Text(
                        current.title,
                        color = Color(0xFFB8D8FF),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (showCategory) {
                    Text(
                        if (locked) "LOCKED · ${channel.category}"
                        else channel.category,
                        color = if (locked) Color(0xFFE3B66D) else Muted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (channel.isFavorite) {
                Icon(
                    Icons.Default.Star,
                    "Favorite",
                    tint = Color(0xFFFFD166),
                    modifier = Modifier.size(15.dp)
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        "Channel actions",
                        tint = Muted
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Watch channel") },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                        onClick = {
                            menuExpanded = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (channel.isFavorite)
                                    "Remove from favorites"
                                else "Add to favorites"
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (channel.isFavorite)
                                    Icons.Default.Star
                                else Icons.Default.StarBorder,
                                null
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onFavorite()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to favorite group") },
                        leadingIcon = {
                            Icon(Icons.Default.CreateNewFolder, null)
                        },
                        onClick = {
                            menuExpanded = false
                            onAddToGroup()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(if (locked) "Unlock channel" else "Lock channel")
                        },
                        leadingIcon = {
                            Icon(
                                if (locked) Icons.Default.LockOpen
                                else Icons.Default.Lock,
                                null
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onLock()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveChannelDetails(
    channel: StreamEntity?,
    programmes: List<ProgrammeEntity>,
    locked: Boolean,
    showLogos: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (channel == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Choose a channel", color = Muted)
        }
        return
    }

    val now = System.currentTimeMillis()
    val current = programmes.firstOrNull {
        now >= it.startUtc && now < it.endUtc
    }
    val next = programmes.firstOrNull {
        it.startUtc >= (current?.endUtc ?: now)
    }

    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF171F29))
            .padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "CHANNEL INFORMATION",
            color = Color(0xFFB8D8FF),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.3.sp
        )

        if (showLogos && channel.logo.isNotBlank()) {
            AsyncImage(
                model = channel.logo,
                contentDescription = "${channel.name} logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(66.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color(0xFF252D36))
                    .padding(8.dp)
            )
        }

        Text(
            channel.name,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Text(channel.category, color = Muted, fontSize = 11.sp)

        if (locked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = Color(0xFFE3B66D))
                Spacer(Modifier.width(6.dp))
                Text("Parental lock enabled", color = Color(0xFFE3B66D), fontSize = 11.sp)
            }
        }

        HorizontalDivider(color = Color(0xFF303943))

        Text("NOW", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(
            current?.title ?: "No current programme data",
            color = Color.White,
            fontSize = 14.sp
        )

        if (current != null) {
            val progress = (
                (now - current.startUtc).toFloat() /
                    (current.endUtc - current.startUtc).coerceAtLeast(1L)
                ).coerceIn(0f, 1f)

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )

            if (current.description.isNotBlank()) {
                Text(
                    current.description,
                    color = Muted,
                    fontSize = 11.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text("NEXT", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(
            next?.title ?: "No upcoming programme data",
            color = Color.White,
            fontSize = 12.sp
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(7.dp))
            Text(if (locked) "Unlock and watch" else "Watch channel")
        }
    }
}
