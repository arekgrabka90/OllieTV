package com.ollie.iptv.ui.theme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
@Composable fun OllieMaterialTheme(content: @Composable () -> Unit) {
 MaterialTheme(colorScheme=darkColorScheme(primary=OllieAccent,onPrimary=OllieAccentInk,primaryContainer=Color(0xFF49301F),onPrimaryContainer=OllieTextPrimary,secondary=Color(0xFFBEB6CC),onSecondary=Color(0xFF201B2A),secondaryContainer=OllieCardSelected,onSecondaryContainer=OllieTextPrimary,background=Ink,onBackground=OllieTextPrimary,surface=Panel,onSurface=OllieTextPrimary,surfaceVariant=OllieCard,onSurfaceVariant=OllieTextSecondary,outline=OllieGlassLine,outlineVariant=Color.White.copy(alpha=.07f),error=OllieLiveRed,onError=Color(0xFF24080C)),shapes=Shapes(extraSmall=RoundedCornerShape(7.dp),small=RoundedCornerShape(11.dp),medium=RoundedCornerShape(16.dp),large=RoundedCornerShape(21.dp),extraLarge=RoundedCornerShape(26.dp)),content=content)
}
@Composable fun OllieGlassPanel(modifier:Modifier=Modifier,selected:Boolean=false,contentPadding:PaddingValues=PaddingValues(14.dp),content:@Composable ColumnScope.()->Unit){
 Surface(modifier=modifier.fillMaxWidth(),color=if(selected) OllieCardSelected else OllieGlass,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,if(selected) OllieAccent.copy(alpha=.48f) else OllieGlassLine.copy(alpha=.75f)),shadowElevation=if(selected) 12.dp else 6.dp){Column(Modifier.padding(contentPadding),content=content)}
}
