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
@Composable
fun SettingsScreen(settings:OllieSettings,onShowLogos:(Boolean)->Unit,onShowCategory:(Boolean)->Unit,onShowRecent:(Boolean)->Unit,onCompactRows:(Boolean)->Unit,onHudTimeout:(Int)->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Personalise your Ollie TV experience",color=Muted)
        Section("Live TV & channel list","Presentation preferences") {
            Toggle("Show channel logos","Display artwork supplied by M3U or Xtream sources.",settings.showChannelLogos,onShowLogos)
            Toggle("Show channel categories","Show the category under each channel.",settings.showChannelCategory,onShowCategory)
            Toggle("Show recently watched","Show the recent channels strip.",settings.showRecentChannels,onShowRecent)
            Toggle("Compact list rows","Reduce row height in channel and VOD lists.",settings.compactRows,onCompactRows)
        }
        Section("Player","Playback overlay") {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("HUD auto-hide delay");Text("${settings.hudTimeoutSeconds} seconds",color=MaterialTheme.colorScheme.secondary)
                }
                Slider(value=settings.hudTimeoutSeconds.toFloat(),onValueChange={onHudTimeout(it.toInt())},valueRange=3f..15f,steps=11)
                Text("The player overlay hides after this period without interaction.",color=Muted,style=MaterialTheme.typography.bodySmall)
            }
        }
        Section("About settings","Saved on this device") {
            Text("Preferences are saved immediately with Android DataStore and remain after restarting the app. Playlist, EPG, favourites, parental locks and recordings retain their existing database storage.",Modifier.padding(14.dp),color=Muted,style=MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable private fun Section(title:String,subtitle:String,content:@Composable ColumnScope.()->Unit) {
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Panel)) {
        Column {
            Column(Modifier.padding(horizontal=14.dp,vertical=12.dp)){Text(title,style=MaterialTheme.typography.titleMedium);Text(subtitle,color=Muted,style=MaterialTheme.typography.bodySmall)}
            HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.25f));content()
        }
    }
}
@Composable private fun Toggle(title:String,description:String,checked:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=9.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)){Text(title);Text(description,color=Muted,style=MaterialTheme.typography.bodySmall)}
        Switch(checked=checked,onCheckedChange=onChange)
    }
}
