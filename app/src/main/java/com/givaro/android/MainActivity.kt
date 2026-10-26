package com.givaro.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF000000)
            ) {
                PlayerScreen()
            }
        }
    }
}

data class Track(val title: String, val artist: String, val duration: String)

@Composable
fun PlayerScreen() {
    var isPlaying by remember { mutableStateOf(false) }
    
    val playlist = listOf(
        Track("Faixa Minimalista", "Givaro Sound", "03:45"),
        Track("Nocturne in Amber", "Givaro Analog", "04:12"),
        Track("Electric Echoes", "Subtle Bass", "02:58")
    )
    
    var currentIndex by remember { mutableStateOf(0) }
    val currentTrack = playlist[currentIndex]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Título Minimalista no Topo
        Text(
            text = "GIVARO",
            color = Color.LightGray,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 6.sp,
            modifier = Modifier.padding(top = 16.dp)
        )

        // Capa / Visualizador Central OLED
        Box(
            modifier = Modifier
                .size(280.dp)
                .background(Color(0xFF0D0D0D), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isPlaying) "A REPRODUZIR" else "PAUSADO",
                    color = Color(0xFFFF9800),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = currentTrack.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentTrack.artist,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }

        // Seção de Controlo de Progresso e Botões
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Slider(
                value = 0.35f,
                onValueChange = {},
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFF9800),
                    activeTrackColor = Color(0xFFFF9800),
                    inactiveTrackColor = Color(0xFF222222)
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "01:24", color = Color.Gray, fontSize = 12.sp)
                Text(text = currentTrack.duration, color = Color.Gray, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Botões de Navegação e Play/Pause
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        currentIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(text = "⏮", color = Color.White, fontSize = 16.sp)
                }

                Button(
                    onClick = { isPlaying = !isPlaying },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    modifier = Modifier
                        .height(56.dp)
                        .width(140.dp),
                    shape = CircleShape
                ) {
                    Text(
                        text = if (isPlaying) "PAUSA" else "PLAY",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }

                Button(
                    onClick = {
                        currentIndex = (currentIndex + 1) % playlist.size
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(text = "⏭", color = Color.White, fontSize = 16.sp)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}
