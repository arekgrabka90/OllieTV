package com.ollie.iptv.ui.home
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ollie.iptv.data.*
import com.ollie.iptv.ui.theme.Muted
import com.ollie.iptv.ui.theme.Panel

@Composable
fun TVHubScreen(channels:List<StreamEntity>,recentChannels:List<StreamEntity>,favorites:List<StreamEntity>,categories:List<String>,selectedCategory:String,selectedChannel:StreamEntity?,programmes:List<ProgrammeEntity>,channelMappings:List<ChannelMappingEntity>,onCategoryChange:(String)->Unit,onChannelClick:(StreamEntity)->Unit) {
    val current=selectedChannel?.let { ch ->
        val epgId=channelMappings.firstOrNull{it.streamId==ch.id}?.xmltvChannelId?.takeIf{it.isNotBlank()}?:ch.tvgId
        programmes.firstOrNull{it.channelId.equals(epgId,true)&&System.currentTimeMillis() in it.startUtc until it.endUtc}
    }
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(bottom=14.dp)) {
        item {
            Row(Modifier.fillMaxWidth().heightIn(min=145.dp).background(Brush.linearGradient(listOf(Color(0xFF24384A),Color(0xFF111923),Color(0xFF101317))),RoundedCornerShape(24.dp)).padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text("OLLIE  /  TELEVISION",color=Color(0xFFB8D8FF),fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp)
                    Text(selectedChannel?.name?:"Your television, beautifully organised",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text(when{selectedChannel==null->"Choose a channel to start watching";current!=null->"NOW  •  ${current.title}";else->selectedChannel.category},color=Color(0xFFB8C2CD),fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                    if(selectedChannel!=null) Text("ON AIR",color=Color(0xFFFF9299),fontSize=9.sp,fontWeight=FontWeight.Bold)
                }
                if(!selectedChannel?.logo.isNullOrBlank()) AsyncImage(selectedChannel!!.logo,selectedChannel.name,contentScale=ContentScale.Fit,modifier=Modifier.size(76.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha=.08f)).padding(9.dp))
                else Icon(Icons.Default.LiveTv,null,tint=Color.White.copy(alpha=.75f),modifier=Modifier.size(58.dp))
            }
        }
        if(recentChannels.isNotEmpty()) item{Rail("Continue watching",recentChannels,selectedChannel?.id,onChannelClick)}
        val fav=favorites.filter{it.kind=="LIVE"}
        if(fav.isNotEmpty()) item{Rail("Your favourites",fav,selectedChannel?.id,onChannelClick)}
        item {
            Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Live television",color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Bold);Text("${channels.size} channels available",color=Muted,fontSize=11.sp)};Icon(Icons.Default.Tune,null,tint=Muted)}
                LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)){items(categories,key={it}){cat->FilterChip(selected=selectedCategory==cat,onClick={onCategoryChange(cat)},label={Text(cat,maxLines=1)})}}
            }
        }
        if(channels.isEmpty()) item{Text("No channels available. Add or enable a playlist in Settings.",Modifier.padding(24.dp),color=Muted)}
        else items(channels,key={it.id}){ch->
            Surface(onClick={onChannelClick(ch)},color=if(selectedChannel?.id==ch.id)Color(0xFF26394B)else Panel,shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,if(selectedChannel?.id==ch.id)Color(0xFFB8D8FF).copy(alpha=.35f)else Color.White.copy(alpha=.04f)),modifier=Modifier.fillMaxWidth()){
                Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.size(43.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF252D36)),contentAlignment=Alignment.Center){if(ch.logo.isNotBlank())AsyncImage(ch.logo,ch.name,contentScale=ContentScale.Fit,modifier=Modifier.fillMaxSize().padding(5.dp))else Icon(Icons.Default.Tv,null,tint=Muted)}
                    Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(ch.name,color=Color.White,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(ch.category,color=Muted,fontSize=11.sp,maxLines=1)}
                    if(selectedChannel?.id==ch.id)Text("PLAYING",color=Color(0xFFB8D8FF),fontSize=9.sp,fontWeight=FontWeight.Bold)else Icon(Icons.Default.PlayCircleOutline,null,tint=Muted)
                }
            }
        }
    }
}
@Composable private fun Rail(title:String,channels:List<StreamEntity>,selected:String?,click:(StreamEntity)->Unit){
    Column(verticalArrangement=Arrangement.spacedBy(9.dp)){Text(title,color=Color.White,fontSize=17.sp,fontWeight=FontWeight.Bold);LazyRow(horizontalArrangement=Arrangement.spacedBy(9.dp)){items(channels,key={it.id}){ch->
        Surface(onClick={click(ch)},color=if(selected==ch.id)Color(0xFF26394B)else Panel,shape=RoundedCornerShape(13.dp),modifier=Modifier.width(164.dp)){
            Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(34.dp).background(Color(0xFF252D36),RoundedCornerShape(8.dp)),contentAlignment=Alignment.Center){if(ch.logo.isNotBlank())AsyncImage(ch.logo,null,contentScale=ContentScale.Fit,modifier=Modifier.fillMaxSize().padding(4.dp))else Icon(Icons.Default.Tv,null,tint=Muted)};Spacer(Modifier.width(8.dp));Text(ch.name,color=Color.White,fontSize=11.sp,maxLines=2,overflow=TextOverflow.Ellipsis)}
        }
    }}}
}
