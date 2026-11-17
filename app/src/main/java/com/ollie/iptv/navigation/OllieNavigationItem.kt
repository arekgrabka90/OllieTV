package com.ollie.iptv.navigation
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ollie.iptv.ui.theme.*
@Composable fun OllieNavigationItem(tab:ScreenTab,selected:Boolean,compact:Boolean=false,bottom:Boolean=false,onClick:()->Unit){
 var focused by remember{mutableStateOf(false)}
 val icon=when(tab){ScreenTab.HUB->Icons.Default.Home;ScreenTab.LIVE->Icons.Default.Tv;ScreenTab.GUIDE->Icons.Default.CalendarMonth;ScreenTab.MOVIES->Icons.Default.Movie;ScreenTab.SERIES->Icons.Default.VideoLibrary;ScreenTab.FAVORITES->Icons.Default.Star;ScreenTab.RECORDINGS->Icons.Default.FiberManualRecord;ScreenTab.SETTINGS->Icons.Default.Settings}
 val active=selected||focused
 Surface(onClick=onClick,color=when{selected->OllieCardSelected;focused->OllieCard;else->Color.Transparent},border=when{selected->BorderStroke(1.dp,OllieAccent.copy(alpha=.52f));focused->BorderStroke(1.dp,OllieAccent.copy(alpha=.3f));else->null},shape=RoundedCornerShape(13.dp),modifier=(if(bottom) Modifier.width(68.dp).padding(horizontal=1.dp) else if(compact) Modifier.width(64.dp).padding(vertical=3.dp) else Modifier.fillMaxWidth().padding(vertical=3.dp)).onFocusChanged{focused=it.isFocused}){
 if(compact||bottom) Column(Modifier.padding(horizontal=3.dp,vertical=if(bottom)5.dp else 10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Icon(icon,null,tint=if(active)OllieAccent else Muted);if(bottom)Text(tab.label,color=if(active)Color.White else Muted,fontSize=9.sp,maxLines=1)}
 else Row(Modifier.fillMaxWidth().padding(horizontal=13.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=if(active)OllieAccent else Muted,modifier=Modifier.size(20.dp));Spacer(Modifier.width(13.dp));Text(tab.label,color=if(active)Color.White else Muted,fontSize=14.sp)}
 }
}
