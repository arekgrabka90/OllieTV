package com.ollie.iptv.player.ui
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.ollie.iptv.data.StreamEntity
import com.ollie.iptv.ui.theme.Muted
import kotlinx.coroutines.delay

@Composable
fun OllieMiniPlayer(player:ExoPlayer,stream:StreamEntity,onExpand:()->Unit,onClose:()->Unit){
    var playing by remember(player){mutableStateOf(player.isPlaying)}
    val view=remember(player){mutableStateOf<PlayerView?>(null)}
    LaunchedEffect(player){while(true){playing=player.isPlaying;delay(450)}}
    DisposableEffect(player){onDispose{view.value?.player=null}}
    Surface(onClick=onExpand,modifier=Modifier.fillMaxWidth().padding(top=10.dp),color=Color(0xE9161D26),shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=.13f)),shadowElevation=14.dp){
        Row(Modifier.fillMaxWidth().padding(9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            AndroidView(factory={ctx->PlayerView(ctx).apply{useController=false;this.player=player;resizeMode=androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT;setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);view.value=this}},update={it.player=player;view.value=it},modifier=Modifier.width(110.dp).height(64.dp))
            Column(Modifier.weight(1f)){Text("NOW PLAYING",color=Color(0xFFB8D8FF),fontSize=8.sp,letterSpacing=1.2.sp);Text(stream.name,color=Color.White,fontSize=12.sp,maxLines=1);Text(stream.category,color=Muted,fontSize=10.sp,maxLines=1)}
            IconButton(onClick={if(player.isPlaying)player.pause()else player.play();playing=player.isPlaying}){Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,if(playing)"Pause" else "Play",tint=Color.White)}
            IconButton(onClick=onExpand){Icon(Icons.Default.Fullscreen,"Full screen",tint=Color(0xFFB8D8FF))}
            IconButton(onClick=onClose){Icon(Icons.Default.Close,"Stop",tint=Muted)}
        }
    }
}
