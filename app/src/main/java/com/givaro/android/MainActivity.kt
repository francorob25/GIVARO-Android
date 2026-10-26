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
                color = Color(0xFF000000) // Preto absoluto OLED
            ) {
                PlayerScreen()
            }
        }
    }
}

// Estrutura de dados corrigida (sem o ponto)
data class Track(val title: String, val artist: String, val duration: String)

@Composable
fun PlayerScreen() {
    var isPlaying by remember { mutableStateOf(false) }
    
    // Lista de músicas simulada para o nosso leitor Walkman
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
        // Título Minimalista
        Text(
            text = "GIVARO",
            color = Color.LightGray,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 4.sp,
            modifier = Modifier.padding(top = 16.dp)
        )

        // Visualizador / Capa Central Dinâmica
        Box(
            modifier = Modifier
                .size(260.dp)
                .background(Color(0xFF1A1A1A), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isPlaying) "A Reproduzir" else "Pausado",
                    color = Color(0xFFFF9800),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = currentTrack.title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = currentTrack.artist,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }

        // Controlos e Barra de Progresso
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
                    inactiveTrackColor = Color(0xFF333333)
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "01:24", color = Color.Gray, fontSize = 12.sp)
                Text(text = currentTrack.duration, color = Color.Gray, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Botões de Navegação (Anterior, Play/Pause, Seguinte)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botão Anterior
                Button(
                    onClick = {
                        currentIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                    modifier = Modifier.height(45.dp),
                    shape = CircleShape
                ) {
                    Text(text = "◄", color = Color.White)
                }

                // Botão Principal Play/Pause
                Button(
                    onClick = { isPlaying = !isPlaying },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    modifier = Modifier.height(50.dp).width(130.dp),
                    shape = CircleShape
                ) {
                    Text(
                        text = if (isPlaying) "PAUSA" else "PLAY",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }

                // Botão Seguinte
                Button(
                    onClick = {
                        currentIndex = (currentIndex + 1) % playlist.size
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                    modifier = Modifier.height(45.dp),
                    shape = CircleShape
                ) {
                    Text(text = "►", color = Color.White)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

