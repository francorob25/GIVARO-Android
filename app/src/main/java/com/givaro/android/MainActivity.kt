package com.givaro.android

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val reqLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            reqLauncher.launch(perm)
        }
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF000000)) {
                PlayerScreen()
            }
        }
    }
}

data class Track(
    val id: Long,
    val albumId: Long,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val uri: android.net.Uri
) {
    val durationFormatted: String
        get() = String.format("%02d:%02d", durationSec / 60, durationSec % 60)
}

@Composable
fun PlayerScreen() {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("player") }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var volume by remember { mutableFloatStateOf(0.7f) }
    var isShuffleOn by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(0) }
    
    val accentColor = Color(0xFFFF9800)
    var favoriteTrackIds = remember { mutableStateOf(setOf<Long>()) }

    val mediaPlayer = remember { MediaPlayer() }
    var equalizer by remember { mutableStateOf<Equalizer?>(null) }

    val playlist = remember {
        val list = mutableListOf<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            MediaStore.Audio.Media.TITLE + " ASC"
        )
        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val albumId = it.getLong(albumIdCol)
                val title = it.getString(titleCol) ?: "Desconhecido"
                val artist = it.getString(artistCol) ?: "Artista Desconhecido"
                val durationMs = it.getLong(durationCol)
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                list.add(Track(id, albumId, title, artist, (durationMs / 1000).toInt(), contentUri))
            }
        }
        
        if (list.isEmpty()) {
            listOf(
                Track(0L, 0L, "Nocturne in Amber", "Givaro Analog", 252, android.net.Uri.EMPTY),
                Track(1L, 0L, "Midnight Drive", "Givaro Sound", 202, android.net.Uri.EMPTY)
            )
        } else {
            list
        }
    }
    
    var currentIndex by remember { mutableIntStateOf(0) }
    val currentTrack = playlist.getOrElse(currentIndex) { playlist[0] }
    val isCurrentFavorite = favoriteTrackIds.value.contains(currentTrack.id)

    val albumArtBitmap = remember(currentTrack.albumId) {
        try {
            val albumUri = ContentUris.withAppendedId(
                android.net.Uri.parse("content://media/external/audio/albumart"),
                currentTrack.albumId
            )
            BitmapFactory.decodeStream(context.contentResolver.openInputStream(albumUri))?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    LaunchedEffect(currentIndex, isPlaying) {
        if (currentTrack.uri != android.net.Uri.EMPTY) {
            try {
                if (isPlaying) {
                    if (!mediaPlayer.isPlaying) {
                        try {
                            mediaPlayer.start()
                        } catch (e: Exception) {
                            mediaPlayer.reset()
                            mediaPlayer.setDataSource(context, currentTrack.uri)
                            mediaPlayer.prepare()
                            mediaPlayer.seekTo(currentPositionMs.toInt())
                            mediaPlayer.start()
                            mediaPlayer.setVolume(volume, volume)
                            if (equalizer == null) {
                                try { equalizer = Equalizer(0, mediaPlayer.audioSessionId).apply { enabled = true } } catch (ex: Exception) {}
                            }
                        }
                    }
                } else {
                    if (mediaPlayer.isPlaying) mediaPlayer.pause()
                }
            } catch (e: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                equalizer?.release()
                mediaPlayer.release()
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(isPlaying, currentIndex) {
        while (isPlaying) {
            delay(250L)
            if (mediaPlayer.isPlaying) {
                currentPositionMs = mediaPlayer.currentPosition.toLong()
            } else {
                currentPositionMs += 250L
            }
            val totalMs = currentTrack.durationSec * 1000L
            if (totalMs > 0 && currentPositionMs >= totalMs) {
                currentPositionMs = 0L
                when (repeatMode) {
                    2 -> { mediaPlayer.seekTo(0); mediaPlayer.start() }
                    else -> { currentIndex = if (isShuffleOn) playlist.indices.random() else (currentIndex + 1) % playlist.size }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050505))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { currentScreen = "equalizer" }, contentPadding = PaddingValues(4.dp)) {
                        Text(text = "EQ", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(onClick = { currentScreen = "favorites" }, contentPadding = PaddingValues(4.dp)) {
                        Text(text = "FAV", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    }
                }
                Text(text = "G I V A R O", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Light, letterSpacing = 6.sp)
                IconButton(onClick = { currentScreen = "playlist" }, modifier = Modifier.size(36.dp)) {
                    Text(text = "☰", color = accentColor, fontSize = 18.sp)
                }
            }

            // Capa de Álbum Central (Estilo Premium da Imagem)
            Box(
                modifier = Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0A0A0A)),
                contentAlignment = Alignment.Center
            ) {
                if (albumArtBitmap != null) {
                    Image(bitmap = albumArtBitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        Text(text = "WAV 192 kHz / 24 bit", color = Color.Gray, fontSize = 9.sp, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = currentTrack.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = currentTrack.artist, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }

            // Bloco de Controlos e Informações Harmonizado
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                
                // Hi-Res Badge & Metadados
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.fillMaxWidth(0.82f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xFF221A00)).padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(text = "HR", color = accentColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = currentTrack.artist, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(text = currentTrack.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "Digital Music Player  •  ${currentIndex + 1}/${playlist.size}", color = Color.DarkGray, fontSize = 10.sp)
                    }

                    IconButton(
                        onClick = {
                            val set = favoriteTrackIds.value.toMutableSet()
                            if (isCurrentFavorite) set.remove(currentTrack.id) else set.add(currentTrack.id)
                            favoriteTrackIds.value = set
                        }
                    ) {
                        Text(text = if (isCurrentFavorite) "♥" else "♡", color = if (isCurrentFavorite) accentColor else Color.DarkGray, fontSize = 22.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Barra de Progresso Fina
                val totalMs = currentTrack.durationSec * 1000L
                val progressFraction = if (totalMs > 0) (currentPositionMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f
                
                Slider(
                    value = progressFraction,
                    onValueChange = { fraction ->
                        val target = (fraction * totalMs).toLong()
                        currentPositionMs = target
                        try { mediaPlayer.seekTo(target.toInt()) } catch (e: Exception) {}
                    },
                    colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor, inactiveTrackColor = Color(0xFF1C1C1C)),
                    modifier = Modifier.height(12.dp)
                )

                // Tempos
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    val secs = (currentPositionMs / 1000).toInt()
                    Text(text = String.format("%02d:%02d", secs / 60, secs % 60), color = Color.Gray, fontSize = 10.sp)
                    Text(text = currentTrack.durationFormatted, color = Color.Gray, fontSize = 10.sp)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Controlo de Volume
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "VOL", color = Color.DarkGray, fontSize = 9.sp, letterSpacing = 2.sp)
                    Slider(
                        value = volume,
                        onValueChange = { volume = it; mediaPlayer.setVolume(volume, volume) },
                        modifier = Modifier.fillMaxWidth(0.9f).height(10.dp),
                        colors = SliderDefaults.colors(thumbColor = accentColor.copy(alpha = 0.7f), activeTrackColor = accentColor.copy(alpha = 0.5f), inactiveTrackColor = Color(0xFF141414))
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Botões de Transporte Circular
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(19.dp)).background(Color(0xFF121212)).clickable { repeatMode = (repeatMode + 1) % 3 },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = if (repeatMode == 2) "1" else "∞", color = if (repeatMode > 0) accentColor else Color.DarkGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Box(
                        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(21.dp)).background(Color(0xFF121212)).clickable {
                            currentIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
                            currentPositionMs = 0L
                            isPlaying = true
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "◀◀", color = Color.LightGray, fontSize = 12.sp)
                    }
                    
                    Box(
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(28.dp)).background(accentColor).clickable { isPlaying = !isPlaying },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = if (isPlaying) "❚❚" else "▶", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Box(
                        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(21.dp)).background(Color(0xFF121212)).clickable {
                            currentIndex = if (isShuffleOn) playlist.indices.random() else (currentIndex + 1) % playlist.size
                            currentPositionMs = 0L
                            isPlaying = true
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "▶▶", color = Color.LightGray, fontSize = 12.sp)
                    }
                    
                    Box(
                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(19.dp)).background(Color(0xFF121212)).clickable { isShuffleOn = !isShuffleOn },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "⤮", color = if (isShuffleOn) accentColor else Color.DarkGray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Barra de Navegação Inferior (Estilo da Imagem Conceito)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { currentScreen = "player" }) {
                    Text(text = "🎵", fontSize = 18.sp)
                }
                IconButton(onClick = { currentScreen = "equalizer" }) {
                    Text(text = "⏱", fontSize = 18.sp)
                }
                IconButton(onClick = { currentScreen = "playlist" }) {
                    Text(text = "☰", color = accentColor, fontSize = 20.sp)
                }
                IconButton(onClick = { currentScreen = "favorites" }) {
                    Text(text = "♥", fontSize = 18.sp)
                }
            }
        }
    }
}
