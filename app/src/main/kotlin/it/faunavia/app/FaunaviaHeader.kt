package it.faunavia.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object FaunaviaColors {
    val Background = Color(0xFFF4F7F2)
    val Green = Color(0xFF1F5C3F)
    val Navigation = Color(0xFFE4EEE5)
    val Ink = Color(0xFF26382E)
    val Muted = Color(0xFF52675A)
    val Error = Color(0xFF9B1C1C)
}

@Composable
internal fun FaunaviaHeader(title: String, titleTag: String,
    background: Color = FaunaviaColors.Green, contentColor: Color = Color.White) {
    Box(Modifier.fillMaxWidth().height(104.dp).background(background).padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart) {
        Column {
            Text("Faunavia", color = contentColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(title, modifier = Modifier.testTag(titleTag), color = contentColor,
                fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
    }
}
