package com.ollie.iptv.ui.live
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ollie.iptv.data.ProgrammeEntity
import com.ollie.iptv.data.StreamEntity
import com.ollie.iptv.ui.LiveSortMode
import com.ollie.iptv.ui.theme.Muted
import com.ollie.iptv.ui.theme.Panel

@Composable
fun LiveTvScreen(
    channels: List<StreamEntity>,
    recentChannels: List<StreamEntity>,
    categories: List<String>,
    selectedCategory: String,
    search: String,
    sortMode: LiveSortMode,
    programmes: List<ProgrammeEntity>,
    lockedChannelIds: Set<String>,
    expandedLayout: Boolean,
    onSearchChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onSortChange: (LiveSortMode) -> Unit,
    onPlay: (StreamEntity) -> Unit,
    onFavorite: (StreamEntity) -> Unit,
    onLockToggle: (StreamEntity) -> Unit,
    onAddToGroup:(StreamEntity)->Unit,
    showLogos:Boolean=true,showCategory:Boolean=true,showRecentChannels:Boolean=true,compactRows:Boolean=false
) {
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    val preview = channels.firstOrNull { it.id == previewId } ?: channels.firstOrNull()
    LaunchedEffect(channels) {
        if (previewId != null && channels.none { it.id == previewId }) previewId = channels.firstOrNull()?.id
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = search,
                onValueChange = onSearchChange,
                modifier = Modifier.weight(1f).heightIn(max = 58.dp),
                placeholder = { Text("Search channels") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )
            SortMenu(sortMode, onSortChange)
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(categories, key = { it }) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onCategoryChange(category) },
                    label = { Text(category, maxLines = 1) }
                )
            }
        }

        if (showRecentChannels && recentChannels.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, tint = Muted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("RECENT CHANNELS", color = Muted, fontSize = 10.sp)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(recentChannels, key = { it.id }) { channel ->
                        Surface(
                            onClick = { onPlay(channel) },
                            color = Panel,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.widthIn(min = 118.dp, max = 190.dp).height(42.dp)
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                if(showLogos&&channel.logo.isNotBlank())AsyncImage(model=channel.logo,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.size(22.dp)) else Icon(Icons.Default.Tv,null,tint=Muted,modifier=Modifier.size(16.dp))
                                Spacer(Modifier.width(7.dp))
                                Text(channel.name, maxLines = 1, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        if (channels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No channels match these filters.", color = Muted)
            }
        } else if (expandedLayout) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChannelList(
                    channels = channels,
                    lockedChannelIds = lockedChannelIds,
                    selectedId = preview?.id,
                    onSelect = { previewId = it.id },
                    onPlay = onPlay,
                    onFavorite = onFavorite,
                    onLockToggle = onLockToggle,
                    onAddToGroup=onAddToGroup,showLogos=showLogos,showCategory=showCategory,compactRows=compactRows,
                    modifier=Modifier.weight(0.46f).fillMaxHeight()
                )
                VerticalDivider(color = Color(0xFF303943))
                ChannelPreview(
                    channel = preview,
                    programmes = programmes,
                    locked = preview?.id?.let { it in lockedChannelIds } == true,
                    onPlay = { preview?.let(onPlay) },
                    modifier = Modifier.weight(0.54f).fillMaxHeight()
                )
            }
        } else {
            ChannelList(
                channels = channels,
                lockedChannelIds = lockedChannelIds,
                selectedId = preview?.id,
                onSelect = { previewId = it.id; onPlay(it) },
                onPlay = onPlay,
                onFavorite = onFavorite,
                onLockToggle = onLockToggle,
                onAddToGroup=onAddToGroup,showLogos=showLogos,showCategory=showCategory,compactRows=compactRows,
                modifier=Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ChannelList(
    channels: List<StreamEntity>,
    lockedChannelIds: Set<String>,
    selectedId: String?,
    onSelect: (StreamEntity) -> Unit,
    onPlay: (StreamEntity) -> Unit,
    onFavorite: (StreamEntity) -> Unit,
    onLockToggle: (StreamEntity) -> Unit,
    onAddToGroup:(StreamEntity)->Unit,
    showLogos:Boolean=true,showCategory:Boolean=true,compactRows:Boolean=false,
    modifier:Modifier=Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        items(channels, key = { it.id }, contentType = { "live-channel" }) { channel ->
            var focused by remember { mutableStateOf(false) }
            val locked = channel.id in lockedChannelIds
            Surface(
                onClick = { onSelect(channel) },
                color = when {
                    selectedId == channel.id -> Color(0xFF283746)
                    focused -> Color(0xFF202832)
                    else -> Panel
                },
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
            ) {
                var menuExpanded by remember(channel.id) { mutableStateOf(false) }
                Row(
                    Modifier.padding(start=if(compactRows)7.dp else 11.dp,end=5.dp,top=if(compactRows)4.dp else 7.dp,bottom=if(compactRows)4.dp else 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if(showLogos&&channel.logo.isNotBlank())AsyncImage(model=channel.logo,contentDescription="${channel.name} logo",contentScale=ContentScale.Crop,modifier=Modifier.size(if(compactRows)30.dp else 38.dp).clip(RoundedCornerShape(6.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(channel.name,maxLines=1,fontSize=13.sp)
                        if(showCategory) Text(
                            if (locked) "LOCKED • ${channel.category}" else channel.category,
                            maxLines = 1,
                            color = if (locked) Color(0xFFE3B66D) else Muted,
                            fontSize = 10.sp
                        )
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, "Channel actions", tint = Muted)
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Watch channel") },
                                leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                                onClick = { menuExpanded = false; onPlay(channel) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (channel.isFavorite) "Remove favorite" else "Add favorite") },
                                leadingIcon = { Icon(if (channel.isFavorite) Icons.Default.Star else Icons.Default.StarBorder, null) },
                                onClick = { menuExpanded = false; onFavorite(channel) }
                            )
                            DropdownMenuItem(
                                text = { Text("Add to favorite group") },
                                leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                                onClick = { menuExpanded = false; onAddToGroup(channel) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (locked) "Unlock channel" else "Lock channel") },
                                leadingIcon = { Icon(if (locked) Icons.Default.LockOpen else Icons.Default.Lock, null) },
                                onClick = { menuExpanded = false; onLockToggle(channel) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelPreview(
    channel: StreamEntity?,
    programmes: List<ProgrammeEntity>,
    locked: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (channel == null) return
    val now = System.currentTimeMillis()
    val channelProgrammes = remember(programmes, channel.tvgId) {
        programmes.filter { it.channelId == channel.tvgId }.sortedBy { it.startUtc }
    }
    val current = channelProgrammes.firstOrNull { now >= it.startUtc && now < it.endUtc }
    val next = channelProgrammes.firstOrNull { it.startUtc >= (current?.endUtc ?: now) }

    Column(modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("CHANNEL PREVIEW", color = Muted, fontSize = 10.sp)
        Text(channel.name, style = MaterialTheme.typography.headlineSmall)
        Text(channel.category, color = Muted, fontSize = 12.sp)
        if (locked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = Color(0xFFE3B66D))
                Spacer(Modifier.width(7.dp))
                Text("Parental lock enabled", color = Color(0xFFE3B66D))
            }
        }
        HorizontalDivider(color = Color(0xFF303943))
        Text("NOW", color = Muted, fontSize = 10.sp)
        Text(current?.title ?: "No current programme data", fontSize = 16.sp)
        if (current != null) {
            val progress = ((now - current.startUtc).toFloat() / (current.endUtc - current.startUtc).coerceAtLeast(1L))
                .coerceIn(0f, 1f)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            if (current.description.isNotBlank()) Text(current.description, color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(if (current == null) "UP NEXT" else "NEXT", color = Muted, fontSize = 10.sp)
        Text(next?.title ?: "No upcoming programme data", fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Button(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(7.dp))
            Text(if (locked) "Unlock and watch" else "Watch channel")
        }
    }
}

@Composable
private fun SortMenu(value: LiveSortMode, onChange: (LiveSortMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Sort, null)
            Spacer(Modifier.width(5.dp))
            Text("Sort")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                LiveSortMode.SOURCE to "Default",
                LiveSortMode.NAME_ASC to "Name A–Z",
                LiveSortMode.NAME_DESC to "Name Z–A",
                LiveSortMode.CATEGORY to "Category"
            ).forEach { (mode, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onChange(mode); expanded = false },
                    leadingIcon = { if (mode == value) Icon(Icons.Default.Check, null) }
                )
            }
        }
    }
}
