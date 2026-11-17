package com.ollie.iptv.ui.home
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import androidx.compose.runtime.Composable
import coil.compose.AsyncImage
import com.ollie.iptv.data.StreamEntity
import com.ollie.iptv.navigation.ScreenTab
import com.ollie.iptv.ui.theme.*
@Composable fun LandscapeHomeDashboard(channelCount:Int,playlistCount:Int,movieCount:Int,seriesCount:Int,favoriteCount:Int,recordingCount:Int,recentChannels:List<StreamEntity>,onNavigate:(ScreenTab)->Unit,onPlayChannel:(StreamEntity)->Unit){
 Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(14.dp)){
  Column { Text("WELCOME TO OLLIE",color=OllieAccent,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=2.4.sp);Text("Your entertainment hub",color=OllieTextPrimary,fontSize=27.sp,fontWeight=FontWeight.Bold);Text("$channelCount channels  ·  $playlistCount playlists  ·  $movieCount movies  ·  $seriesCount series",color=Muted,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis) }
  Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(13.dp)){
   Surface(onClick={onNavigate(ScreenTab.LIVE)},modifier=Modifier.weight(1.4f).fillMaxHeight(),color=OllieCard,shape=RoundedCornerShape(21.dp),border=androidx.compose.foundation.BorderStroke(1.dp,OllieAccent.copy(alpha=.28f))){Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF49301F),Color(0xFF211E2D)))).padding(22.dp)){Column(Modifier.align(Alignment.BottomStart),verticalArrangement=Arrangement.spacedBy(7.dp)){Icon(Icons.Default.LiveTv,null,tint=OllieAccent,modifier=Modifier.size(35.dp));Text("LIVE TELEVISION",color=Color.White,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.8.sp);Text("Watch your channels",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Bold);Text("$channelCount available channels",color=Muted,fontSize=12.sp)};Icon(Icons.Default.ChevronRight,null,tint=Color.White,modifier=Modifier.align(Alignment.TopEnd))}}
   Column(Modifier.weight(1f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(11.dp)){DashTile("Movies","$movieCount titles",Icons.Default.Movie,Modifier.weight(1f)){onNavigate(ScreenTab.MOVIES)};DashTile("Series","$seriesCount titles",Icons.Default.VideoLibrary,Modifier.weight(1f)){onNavigate(ScreenTab.SERIES)};Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(11.dp)){DashCount("Favorites",favoriteCount,Icons.Default.Star,Modifier.weight(1f)){onNavigate(ScreenTab.FAVORITES)};DashCount("Recordings",recordingCount,Icons.Default.FiberManualRecord,Modifier.weight(1f)){onNavigate(ScreenTab.RECORDINGS)}}}
  }
  if(recentChannels.isNotEmpty()) Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text("RECENT CHANNELS",color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Bold,letterSpacing=1.2.sp);Spacer(Modifier.weight(1f));TextButton(onClick={onNavigate(ScreenTab.LIVE)}){Text("View all",color=OllieAccent)}};LazyRow(horizontalArrangement=Arrangement.spacedBy(9.dp)){items(recentChannels.take(10),key={it.id}){ch->Surface(onClick={onPlayChannel(ch)},color=OllieCard,shape=RoundedCornerShape(12.dp)){Row(Modifier.widthIn(min=165.dp,max=230.dp).padding(9.dp),verticalAlignment=Alignment.CenterVertically){if(ch.logo.isNotBlank())AsyncImage(ch.logo,contentDescription=ch.name,contentScale=ContentScale.Fit,modifier=Modifier.size(36.dp).clip(RoundedCornerShape(7.dp)))else Icon(Icons.Default.Tv,null,tint=OllieAccent,modifier=Modifier.size(36.dp));Spacer(Modifier.width(8.dp));Column(Modifier.weight(1f)){Text(ch.name,color=Color.White,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(ch.category,color=Muted,fontSize=10.sp,maxLines=1)}}}}}}
 }
}
@Composable private fun DashTile(title:String,sub:String,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier=Modifier,onClick:()->Unit){Surface(onClick=onClick,modifier=modifier.fillMaxWidth(),color=OllieCard,shape=RoundedCornerShape(17.dp)){Row(Modifier.fillMaxSize().padding(15.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=OllieAccent,modifier=Modifier.size(28.dp));Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(title,color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold);Text(sub,color=Muted,fontSize=11.sp)};Icon(Icons.Default.ChevronRight,null,tint=Muted)}}}
@Composable private fun DashCount(title:String,count:Int,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier=Modifier,onClick:()->Unit){Surface(onClick=onClick,modifier=modifier.fillMaxHeight(),color=OllieCard,shape=RoundedCornerShape(15.dp)){Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.Center){Icon(icon,null,tint=OllieAccent);Text("$count",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold);Text(title,color=Muted,fontSize=10.sp)}}}
