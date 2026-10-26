package com.givaro.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
    var currentScreen by remember { mutableStateOf("player") } // "player" ou "playlist"
    
    val playlist = listOf(
        Track("Faixa Minimalista", "Givaro Sound", "03:45"),
        Track("Nocturne in Amber", "Givaro Analog", "04:12"),
        Track("Electric Echoes", "Subtle Bass", "02:58"),
        Track("Analog Sunset", "Givaro Sound", "05:01"),
        Track("Midnight Circuit", "Retro Device", "03:30")
    )
    
    var currentIndex by remember { mutableStateOf(0) }
    val currentTrack = playlist[currentIndex]

    Box(modifier = Modifier.fillMaxSize()) {
        if (currentScreen == "player") {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Topo com botão para abrir a Playlist
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(32.dp))
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

                // Controlos
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
                                text = track.duration,
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
