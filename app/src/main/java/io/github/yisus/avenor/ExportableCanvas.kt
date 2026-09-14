package io.github.yisus.avenor

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun RecapExportCard(
    topArtist: String,
    topSong: String,
    totalMinutes: Long,
    modifier: Modifier = Modifier
) {
    val gradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF2C106B),
            Color(0xFFE040FB)
        )
    )

    Box(
        modifier = modifier
            .width(1080.dp / 3) // Scale down for preview
            .height(1920.dp / 3)
            .clip(RoundedCornerShape(24.dp))
            .background(gradient)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Avenor Recap",
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black
            )
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Top Artist",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    topArtist,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Top Song",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    topSong,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    totalMinutes.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Minutes Listened",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            Text(
                "#AvenorAudio",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
