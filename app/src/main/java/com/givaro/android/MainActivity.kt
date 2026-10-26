package com.givaro.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

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

data class Track(val title: String, val artist: String, val durationSec: Int) {
    val durationFormatted: String
        get() {
            val mins = durationSec / 60
            val secs = durationSec % 60
            return String.format("%02d:%02d", mins, secs)
        }
}

@Composable
fun PlayerScreen() {
    var isPlaying by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("player") } // "player" ou "playlist"
    var currentPositionSec by remember { mutableStateOf(45) } // progresso em segundos
    var volume by remember { mutableStateOf(0.7f) }
    var isShuffleOn by remember { mutableStateOf(false) }
    var isRepeatOn by remember { mutableStateOf(false) }
    
    val playlist = listOf(
        Track("Faixa Minimalista", "Givaro Sound", 225),
        Track("Nocturne in Amber", "Givaro Analog", 252),
        Track("Electric Echoes", "Subtle Bass", 178),
        Track("Analog Sunset", "Givaro Sound", 301),
        Track("Midnight Circuit", "Retro Device", 210)
    )
    
    var currentIndex by remember { mutableStateOf(0) }
    val currentTrack = playlist[currentIndex]

    // Simulação do avanço do tempo de reprodução quando ativo
    LaunchedEffect(isPlaying, currentIndex) {
        while (isPlaying) {
            delay(1000L)
            if (currentPositionSec < currentTrack.durationSec) {
                currentPositionSec++
            } else {
                if (isRepeatOn) {
                    currentPositionSec = 0
                } else {
                    currentIndex = if (isShuffleOn) playlist.indices.random() else (currentIndex + 1) % playlist.size
                    currentPositionSec = 0
                }
            }
        }
    }

    // Animação subtil do anel OLED quando a tocar
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPlaying) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (currentScreen == "player") {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Topo com Shuffle, Título e Menu Playlist
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { isShuffleOn = !isShuffleOn }) {
                        Text(
                            text = "🔀",
                            color = if (isShuffleOn) Color(0xFFFF9800) else Color.Gray,
                            fontSize = 18.sp
                        )
                    }
                    
                    Text(
                        text = "GIVARO",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 6.sp
                    )
                    
                    IconButton(onClick = { currentScreen = "playlist" }) {
                        Text(text = "☰", color = Color(0xFFFF9800), fontSize = 20.sp)
                    }
                }

                // Capa / Visualizador Central OLED com Anel Animado
                Box(
                    modifier = Modifier
                        .size(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Anel exterior decorativo em âmbar subtil
                    Canvas(modifier = Modifier.size(250.dp * pulseScale)) {
                        drawCircle(
                            color = if (isPlaying) Color(0xFFFF9800).copy(alpha = 0.3f) else Color(0xFF1A1A1A),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .background(Color(0xFF0A0A0A), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isPlaying) "A REPRODUZIR" else "PAUSADO",
                                color = Color(0xFFFF9800),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currentTrack.title,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentTrack.artist,
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Controlos de Progresso, Volume e Modos
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val progressFraction = if (currentTrack.durationSec > 0) currentPositionSec.toFloat() / currentTrack.durationSec else 0f
                    
                    Slider(
                        value = progressFraction,
                        onValueChange = { newFraction ->
                            currentPositionSec = (newFraction * currentTrack.durationSec).toInt()
                        },
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
                        val currentMins = currentPositionSec / 60
                        val currentSecs = currentPositionSec % 60
                        Text(text = String.format("%02d:%02d", currentMins, currentSecs), color = Color.Gray, fontSize = 12.sp)
                        Text(text = currentTrack.durationFormatted, color = Color.Gray, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Controlo de Volume compacto
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Vol", color = Color.Gray, fontSize = 12.sp)
                        Slider(
                            value = volume,
                            onValueChange = { volume = it },
                            modifier = Modifier.fillMaxWidth(0.85f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.LightGray,
                                activeTrackColor = Color.LightGray,
                                inactiveTrackColor = Color(0xFF222222)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Botões de Navegação completos
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { isRepeatOn = !isRepeatOn }) {
                            Text(
                                text = "🔁",
                                color = if (isRepeatOn) Color(0xFFFF9800) else Color.Gray,
                                fontSize = 18.sp
                            )
                        }

                        Button(
                            onClick = {
                                currentIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
                                currentPositionSec = 0
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(text = "⏮", color = Color.White, fontSize = 16.sp)
                        }

                        Button(
                            onClick = { isPlaying = !isPlaying },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                            modifier = Modifier
                                .height(52.dp)
                                .width(120.dp),
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
                                currentIndex = if (isShuffleOn) playlist.indices.random() else (currentIndex + 1) % playlist.size
                                currentPositionSec = 0
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(text = "⏭", color = Color.White, fontSize = 16.sp)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }
        } else {
            // Ecrã da Lista de Músicas (Playlist)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PLAYLIST",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    )
                    IconButton(onClick = { currentScreen = "player" }) {
                        Text(text = "✕", color = Color(0xFFFF9800), fontSize = 20.sp)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(playlist) { index, track ->
                        val isSelected = index == currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF1A1A1A) else Color(0xFF0A0A0A))
                                .clickable {
                                    currentIndex = index
                                    isPlaying = true
                                    currentPositionSec = 0
                                    currentScreen = "player"
                                }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = track.title,
                                    color = if (isSelected) Color(0xFFFF9800) else Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = track.artist,
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = track.durationFormatted,
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
