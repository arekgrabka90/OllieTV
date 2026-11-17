package com.ollie.iptv.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ollie.iptv.data.OllieSettings
import com.ollie.iptv.ui.theme.Muted
import com.ollie.iptv.ui.theme.Panel
import com.ollie.iptv.ui.theme.OllieGlassPanel

@Composable
fun SettingsScreen(settings:OllieSettings,onShowLogos:(Boolean)->Unit,onShowCategory:(Boolean)->Unit,onShowRecent:(Boolean)->Unit,onCompactRows:(Boolean)->Unit,onHudTimeout:(Int)->Unit,onAddPlaylist:()->Unit,playlistContent:@Composable ()->Unit,epgContent:@Composable ()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("Personalise your Ollie TV experience",color=Muted)
        Section("Sources & playlists","Manage IPTV connections in one place") {
            Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Add M3U playlists, local files or Xtream Codes accounts. Edit, refresh, reorder and disable existing sources below.",color=Muted,style=MaterialTheme.typography.bodySmall)
                Button(onClick=onAddPlaylist,modifier=Modifier.fillMaxWidth()){Text("Add playlist or Xtream account")}
                playlistContent()
            }
        }
        Section("EPG & programme guide","XMLTV sources and automatic channel matching") {
            Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Automatic channel matching is applied only above 90% confidence and when the best result is unambiguous. Manual mappings and exact tvg-id matches take priority.",color=Muted,style=MaterialTheme.typography.bodySmall)
                epgContent()
            }
        }
        Section("Live TV & channel list","Presentation preferences") {
            Toggle("Show channel logos","Display artwork supplied by M3U or Xtream sources.",settings.showChannelLogos,onShowLogos)
            Toggle("Show channel categories","Show the category under each channel.",settings.showChannelCategory,onShowCategory)
            Toggle("Show recently watched","Show the recent channels strip.",settings.showRecentChannels,onShowRecent)
            Toggle("Compact list rows","Reduce row height in channel and VOD lists.",settings.compactRows,onCompactRows)
        }
        Section("Player","Playback overlay") {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("HUD auto-hide delay");Text("${settings.hudTimeoutSeconds} seconds",color=MaterialTheme.colorScheme.secondary)}
                Slider(value=settings.hudTimeoutSeconds.toFloat(),onValueChange={onHudTimeout(it.toInt())},valueRange=3f..15f,steps=11)
                Text("The player overlay hides after this period without interaction.",color=Muted,style=MaterialTheme.typography.bodySmall)
            }
        }
        Section("About settings","Saved on this device") {
            Text("Preferences are saved immediately with Android DataStore. Playlist, EPG, favourites, parental locks and recordings retain their existing database storage.",Modifier.padding(14.dp),color=Muted,style=MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable private fun Section(title:String,subtitle:String,content:@Composable ColumnScope.()->Unit) {
    OllieGlassPanel(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Column {
            Column(Modifier.padding(horizontal=16.dp,vertical=14.dp)) {
                Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text(subtitle,color=Muted,style=MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.55f))
            content()
        }
    }
}
@Composable private fun Toggle(title:String,description:String,checked:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=9.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)){Text(title);Text(description,color=Muted,style=MaterialTheme.typography.bodySmall)}
        Switch(checked=checked,onCheckedChange=onChange)
    }
}
