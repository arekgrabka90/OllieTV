package com.ollie.iptv.data
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
private val Context.ollieSettingsDataStore by preferencesDataStore(name = "ollie_settings")
data class OllieSettings(
    val showChannelLogos:Boolean=true,
    val showChannelCategory:Boolean=true,
    val showRecentChannels:Boolean=true,
    val compactRows:Boolean=false,
    val hudTimeoutSeconds:Int=7
)
class OllieSettingsRepository(private val context:Context) {
    private object Keys {
        val logos=booleanPreferencesKey("show_channel_logos")
        val category=booleanPreferencesKey("show_channel_category")
        val recent=booleanPreferencesKey("show_recent_channels")
        val compact=booleanPreferencesKey("compact_rows")
        val hud=intPreferencesKey("hud_timeout_seconds")
    }
    val settings:Flow<OllieSettings> = context.ollieSettingsDataStore.data.map { p ->
        OllieSettings(p[Keys.logos]?:true,p[Keys.category]?:true,p[Keys.recent]?:true,p[Keys.compact]?:false,(p[Keys.hud]?:7).coerceIn(3,15))
    }
    suspend fun setShowChannelLogos(v:Boolean){context.ollieSettingsDataStore.edit{it[Keys.logos]=v}}
    suspend fun setShowChannelCategory(v:Boolean){context.ollieSettingsDataStore.edit{it[Keys.category]=v}}
    suspend fun setShowRecentChannels(v:Boolean){context.ollieSettingsDataStore.edit{it[Keys.recent]=v}}
    suspend fun setCompactRows(v:Boolean){context.ollieSettingsDataStore.edit{it[Keys.compact]=v}}
    suspend fun setHudTimeoutSeconds(v:Int){context.ollieSettingsDataStore.edit{it[Keys.hud]=v.coerceIn(3,15)}}
}
