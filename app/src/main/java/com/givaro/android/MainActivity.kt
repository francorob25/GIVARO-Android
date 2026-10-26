package com.givaro.android

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(permission)
        }

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

data class Track(val id: Long, val title: String, val artist: String, val durationSec: Int, val uri: android.net.Uri) {
    val durationFormatted: String
        get() {
            val mins = durationSec / 60
            val secs = durationSec % 60
            return String.format("%02d:%02d", mins, secs)
        }
}

@Composable
fun PlayerScreen() {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("player") }
    var currentPositionSec by remember { mutableStateOf(0) }
    var volume by remember { mutableStateOf(0.7f) }
    var isShuffleOn by remember { mutableStateOf(false) }
    var isRepeatOn by remember { mutableStateOf(false) }
    
    // Instância do MediaPlayer gerida no ciclo de vida do Composable
    val mediaPlayer = remember { MediaPlayer() }

    val playlist = remember {
        val list = mutableListOf<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            MediaStore.Audio.Media.TITLE + " ASC"
        )
        cursor?.use {
            const val ID_COL = 0
            const val TITLE_COL = 1
            const val ARTIST_COL = 2
            const val DURATION_COL = 3

            while (it.moveToNext()) {
                val id = it.getLong(ID_COL)
                val title = it.getString(TITLE_COL) ?: "Desconhecido"
                val artist = it.getString(ARTIST_COL) ?: "Artista Desconhecido"
                val durationMs = it.getLong(DURATION_COL)
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                list.add(Track(id, title, artist, (durationMs / 1000).toInt(), contentUri))
            }
        }
        
        if (list.isEmpty()) {
            listOf(
                Track(0L, "Faixa Minimalista", "Givaro Sound", 225, android.net.Uri.EMPTY),
                Track(1L, "Nocturne in Amber", "Givaro Analog", 252, android.net.Uri.EMPTY),
                Track(2L, "Electric Echoes", "Subtle Bass", 178, android.net.Uri.EMPTY)
            )
        } else {
            list
        }
    }
    
    var currentIndex by remember { mutableStateOf(0) }
    val currentTrack = playlist.getOrElse(currentIndex) { playlist[0] }

    // Gestão do motor de áudio MediaPlayer ao mudar de faixa ou estado Play/Pause
    LaunchedEffect(currentIndex, isPlaying) {
        if (currentTrack.uri != android.net.Uri.EMPTY) {
            try {
                if (isPlaying) {
                    if (!mediaPlayer.isPlaying) {
                        // Se já estiver preparado para a mesma música, apenas faz start
                        try {
                            mediaPlayer.start()
                        } catch (e: Exception) {
                            mediaPlayer.reset()
                            mediaPlayer.setDataSource(context, currentTrack.uri)
                            mediaPlayer.prepare()
                            mediaPlayer.start()
                        }
                    }
                } else {
                    if (mediaPlayer.isPlaying) {
                        mediaPlayer.pause()
                    }
                }
            } catch (e: Exception) {
                // Fallback seguro caso o ficheiro falhe
            }
        }
    }

    // Libertar recursos do MediaPlayer ao sair
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    // Sincronização do progresso do tempo
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

                Box(
                    modifier = Modifier.size(260.dp),
                    contentAlignment = Alignment.Center
                ) {
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
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
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentTrack.artist,
                                color = Color.Gray,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

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
                        Text(text = currentTrack.duration
