package com.ollie.iptv.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ollie.iptv.data.StreamEntity
import com.ollie.iptv.data.VodDetails
import com.ollie.iptv.ui.theme.Muted
import com.ollie.iptv.ui.theme.Panel
@Composable
fun VodDetailsScreen(stream:StreamEntity,details:VodDetails?,loading:Boolean,error:String?,compactRows:Boolean,onBack:()->Unit,onPlay:()->Unit,onPlayEpisode:(StreamEntity)->Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back")};Text("Title details",style=MaterialTheme.typography.titleLarge)}
        when {
            loading->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
            details==null->Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text(stream.name,style=MaterialTheme.typography.headlineSmall);Text(error?:"Details are unavailable for this source.",color=Muted)
                Button(onClick=onPlay){Icon(Icons.Default.PlayArrow,null);Spacer(Modifier.width(8.dp));Text("Play anyway")}
            }
            else->Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)){
                Row(Modifier.fillMaxWidth().padding(horizontal=14.dp),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.Top){
                    AsyncImage(model=details.posterUrl.ifBlank{stream.logo}.takeIf{it.isNotBlank()},contentDescription="${stream.name} poster",contentScale=ContentScale.Crop,modifier=Modifier.weight(.42f).widthIn(max=150.dp).aspectRatio(2f/3f).clip(RoundedCornerShape(18.dp)).background(Panel))
                    Column(Modifier.weight(.58f),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text(details.title.ifBlank{stream.name},style=MaterialTheme.typography.headlineSmall)
                        if(details.releaseDate.isNotBlank())Text(details.releaseDate,color=Muted)
                        if(details.genre.isNotBlank())Text(details.genre,color=Muted)
                        if(details.duration.isNotBlank())Text(details.duration,color=Muted)
                        if(details.rating.isNotBlank())Text("Rating: ${details.rating}",color=Muted)
                        if(stream.kind=="MOVIE")Button(onClick=onPlay,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.PlayArrow,null);Spacer(Modifier.width(6.dp));Text("Play")}
                        else Text("${details.seasonCount} seasons • ${details.episodeCount} episodes",color=Muted)
                    }
                }
                if(details.plot.isNotBlank())Detail("Overview",details.plot)
                if(details.director.isNotBlank())Detail("Director",details.director)
                if(details.cast.isNotBlank())Detail("Cast",details.cast)
                if(stream.kind=="SERIES"){
                    Text("Episodes",Modifier.padding(horizontal=14.dp),style=MaterialTheme.typography.titleLarge)
                    if(details.episodes.isEmpty())Text("No episodes returned by provider.",Modifier.padding(horizontal=14.dp),color=Muted)
                    details.episodes.forEach { ep ->
                        Surface(onClick={onPlayEpisode(ep)},color=Panel,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().padding(horizontal=14.dp)){
                            Row(Modifier.padding(horizontal=12.dp,vertical=if(compactRows)8.dp else 13.dp),verticalAlignment=Alignment.CenterVertically){
                                Column(Modifier.weight(1f)){Text(ep.name);Text(ep.category,color=Muted,style=MaterialTheme.typography.bodySmall)}
                                Icon(Icons.Default.PlayArrow,null)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
@Composable private fun Detail(title:String,body:String){Column(Modifier.fillMaxWidth().padding(horizontal=14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(title,style=MaterialTheme.typography.titleMedium);Text(body,color=Muted)}}
