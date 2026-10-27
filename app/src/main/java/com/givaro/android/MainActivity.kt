package com.givaro.android

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioManager
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
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
    var searchQuery by remember { mutableStateOf("") }
    
    val accentColor = Color(0xFFFF9800)
    var favoriteTrackIds = remember { mutableStateOf(setOf<Long>()) }
    var selectedPreset by remember { mutableIntStateOf(0) }
    val presets = listOf("Flat", "Bass Boost", "Clarity", "Hi-Res Audio", "Vocal")

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
                Track(0L, 0L, "Faixa Minimalista", "Givaro Sound", 225, android.net.Uri.EMPTY),
                Track(1L, 0L, "Nocturne in Amber", "Givaro Analog", 252, android.net.Uri.EMPTY)
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

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            "player" -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Bar estilo Walkman
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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

                    // Capa de Álbum Central (Estilo Walkman A306)
                    Box(
                        modifier = Modifier.size(200.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0A0A0A)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (albumArtBitmap != null) {
                            Image(bitmap = albumArtBitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                                Text(text = "WAV 192 kHz / 24 bit", color = Color.Gray, fontSize = 9.sp, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(text = currentTrack.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = currentTrack.artist, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }

                    // Informações detalhadas e controlos de reprodução
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
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = currentTrack.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(text = "Digital Music Player  •  ${currentIndex + 1}/${playlist.size}", color = Color.DarkGray, fontSize = 10.sp)
                            }

                            IconButton(
                                onClick = {
                                    val set = favoriteTrackIds.value.toMutableSet()
                                    if (isCurrentFavorite) set.remove(currentTrack.id) else set.add(currentTrack.id)
                                    favoriteTrackIds.value = set
                                }
                            ) {
                                Text(text = if (isCurrentFavorite) "♥" else "♡", color = if (isCurrentFavorite) accentColor else Color.DarkGray, fontSize = 20.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

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
                            modifier = Modifier.height(16.dp)
                        )

                        // Tempos
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            val secs = (currentPositionMs / 1000).toInt()
                            Text(text = String.format("%02d:%02d", secs / 60, secs % 60), color = Color.Gray, fontSize = 10.sp)
                            Text(text = currentTrack.durationFormatted, color = Color.Gray, fontSize = 10.sp)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Controlo de Volume Discreto
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "VOL", color = Color.DarkGray, fontSize = 9.sp, letterSpacing = 2.sp)
                            Slider(
                                value = volume,
                                onValueChange = { volume = it; mediaPlayer.setVolume(volume, volume) },
                                modifier = Modifier.fillMaxWidth(0.9f).height(14.dp),
                                colors = SliderDefaults.colors(thumbColor = accentColor.copy(alpha = 0.7f), activeTrackColor = accentColor.copy(alpha = 0.5f), inactiveTrackColor = Color(0xFF141414))
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Botões de Transporte Circular Estilo Walkman
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF121212)).clickable { repeatMode = (repeatMode + 1) % 3 },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = if (repeatMode == 2) "1" else "∞", color = if (repeatMode > 0) accentColor else Color.DarkGray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            Box(
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF121212)).clickable {
                                    currentIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
                                    currentPositionMs = 0L
                                    isPlaying = true
                                },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "◀◀", color = Color.LightGray, fontSize = 13.sp)
                            }
                            
                            Box(
                                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(30.dp)).background(accentColor).clickable { isPlaying = !isPlaying },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = if (isPlaying) "❚❚" else "▶", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            Box(
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF121212)).clickable {
                                    currentIndex = if (isShuffleOn) playlist.indices.random() else (currentIndex + 1) % playlist.size
                                    currentPositionMs = 0L
                                    isPlaying = true
                                },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "▶▶", color = Color.LightGray, fontSize = 13.sp)
                            }
                            
                            Box(
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF121212)).clickable { isShuffleOn = !isShuffleOn },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "⤮", color = if (isShuffleOn) accentColor else Color.DarkGray, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            "equalizer" -> {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "AUDIO EQUALIZER", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                        IconButton(onClick = { currentScreen = "player" }) { Text(text = "✕", color = accentColor, fontSize = 20.sp) }
                    }

                    presets.forEachIndexed { index, name ->
                        val isSelected = index == selectedPreset
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(10.dp)).background(if (isSelected) Color(0xFF151515) else Color(0xFF070707)).clickable { selectedPreset = index }.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = name, color = if (isSelected) accentColor else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            if (isSelected) { Text(text = "●", color = accentColor, fontSize = 12.sp) }
                        }
                    }
                }
            }
            "playlist", "favorites" -> {
                val listToShow = if (currentScreen == "favorites") playlist.filter { favoriteTrackIds.value.contains(it.id) } else playlist
                Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = if (currentScreen == "favorites") "FAVORITOS" else "PLAYLIST", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                        IconButton(onClick = { currentScreen = "player" }) { Text(text = "✕", color = accentColor, fontSize = 20.sp) }
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(listToShow) { _, track ->
                            val originalIndex = playlist.indexOf(track)
                            val isSelected = originalIndex == currentIndex
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (isSelected) Color(0xFF151515) else Color(0xFF070707)).clickable {
                                    currentIndex = originalIndex
                                    isPlaying = true
                                    currentPositionMs = 0L
                                    currentScreen = "player"
                                }.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.fillMaxWidth(0.8f)) {
                                    Text(text = track.title, color = if (isSelected) accentColor else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = track.artist, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                                }
                                Text(text = track.durationFormatted, color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
