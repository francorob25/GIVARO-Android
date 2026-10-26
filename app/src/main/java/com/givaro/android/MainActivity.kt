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
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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

data class Track(
    val id: Long,
    val albumId: Long,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val uri: android.net.Uri
) {
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
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var volume by remember { mutableFloatStateOf(0.7f) }
    var isShuffleOn by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(0) } // 0: Off, 1: All, 2: One
    var searchQuery by remember { mutableStateOf("") }
    
    // Cores de Destaque Dinâmicas (Tema Hi-Fi)
    var themeColorIndex by remember { mutableIntStateOf(0) }
    val themeAccentColors = listOf(
        Color(0xFFFF9800), // Âmbar Clássico
        Color(0xFFFFD700), // Dourado Hi-Fi
        Color(0xFFE0E0E0)  // Cyber Mono
    )
    val accentColor = themeAccentColors[themeColorIndex]

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
            projection,
            null,
            null,
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
                Track(1L, 0L, "Nocturne in Amber", "Givaro Analog", 252, android.net.Uri.EMPTY),
                Track(2L, 0L, "Electric Echoes", "Subtle Bass", 178, android.net.Uri.EMPTY)
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
            val inputStream = context.contentResolver.openInputStream(albumUri)
            BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
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
                            
                            try {
                                if (equalizer == null) {
                                    equalizer = Equalizer(0, mediaPlayer.audioSessionId).apply {
                                        enabled = true
                                    }
                                }
                            } catch (eqEx: Exception) {}
                        }
                    }
                } else {
                    if (mediaPlayer.isPlaying) {
                        mediaPlayer.pause()
                    }
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
                    2 -> {
                        mediaPlayer.seekTo(0)
                        mediaPlayer.start()
                    }
                    else -> {
                        currentIndex = if (isShuffleOn) playlist.indices.random() else (currentIndex + 1) % playlist.size
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            "player" -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Topo Walkman Hi-Fi com Indicador de Estado
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { currentScreen = "equalizer" }, contentPadding = PaddingValues(4.dp)) {
                                Text(
                                    text = "EQ",
                                    color = accentColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = { currentScreen = "favorites" }, contentPadding = PaddingValues(4.dp)) {
                                Text(
                                    text = "FAV",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp
                                )
                            }
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isPlaying) {
                                Text(
                                    text = "● ",
                                    color = accentColor,
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                text = "G I V A R O",
                                color = Color.LightGray,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Light,
                                letterSpacing = 8.sp
                            )
                        }
                        
                        IconButton(onClick = { currentScreen = "playlist" }) {
                            Text(text = "☰", color = accentColor, fontSize = 20.sp)
                        }
                    }

                    // Capa Central com Gestos Avançados (Mudar de faixa no swipe horizontal)
                    var dragAmountX by remember { mutableFloatStateOf(0f) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (dragAmountX > 50f) {
                                            currentIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
                                            currentPositionMs = 0L
                                            isPlaying = true
                                        } else if (dragAmountX < -50f) {
                                            currentIndex = if (isShuffleOn) playlist.indices.random() else (currentIndex + 1) % playlist.size
                                            currentPositionMs = 0L
                                            isPlaying = true
                                        }
                                        dragAmountX = 0f
                                    },
                                    onHorizontalDrag = { _, delta -> dragAmountX += delta }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(230.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF0D0D0D))
                                .clickable { currentScreen = "info" },
                            contentAlignment = Alignment.Center
                        ) {
                            if (albumArtBitmap != null) {
                                Image(
                                    bitmap = albumArtBitmap,
                                    contentDescription = "Album Art",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(20.dp)
                                ) {
                                    Text(
                                        text = if (isPlaying) "HI-RES AUDIO" else "PAUSED",
                                        color = accentColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 3.sp
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = currentTrack.title,
                                        color = Color.White,
                                        fontSize = 15.sp,
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
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF1A1A1A))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "FLAC 192 kHz / 24-bit",
                                            color = accentColor.copy(alpha = 0.8f),
                                            fontSize = 9.sp,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Controlo de Progresso, Volume e Transporte Estilo Sony Signature
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.fillMaxWidth(0.82f)) {
                                Text(
                                    text = currentTrack.title,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = currentTrack.artist,
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                            
                            IconButton(
                                onClick = {
                                    val currentSet = favoriteTrackIds.value.toMutableSet()
                                    if (isCurrentFavorite) currentSet.remove(currentTrack.id)
                                    else currentSet.add(currentTrack.id)
                                    favoriteTrackIds.value = currentSet
                                }
                            ) {
                                Text(
                                    text = if (isCurrentFavorite) "♥" else "♡",
                                    color = if (isCurrentFavorite) accentColor else Color.DarkGray,
                                    fontSize = 20.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        val totalMs = currentTrack.durationSec * 1000L
                        val progressFraction = if (totalMs > 0) (currentPositionMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f
                        
                        Slider(
                            value = progressFraction,
                            onValueChange = { newFraction ->
                                val targetMs = (newFraction * totalMs).toLong()
                                currentPositionMs = targetMs
                                if (currentTrack.uri != android.net.Uri.EMPTY) {
                                    try { mediaPlayer.seekTo(targetMs.toInt()) } catch (e: Exception) {}
                                }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = accentColor,
                                activeTrackColor = accentColor,
                                inactiveTrackColor = Color(0xFF1C1C1C)
                            )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val currentSecsTotal = (currentPositionMs / 1000).toInt()
                            Text(text = String.format("%02d:%02d", currentSecsTotal / 60, currentSecsTotal % 60), color = Color.Gray, fontSize = 11.sp)
                            Text(text = currentTrack.durationFormatted, color = Color.Gray, fontSize = 11.sp)
                        }

                        // Slider de Volume Minimalista
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "VOL", color = Color.DarkGray, fontSize = 9.sp, letterSpacing = 2.sp)
                            Slider(
                                value = volume,
                                onValueChange = { 
                                    volume = it
                                    mediaPlayer.setVolume(volume, volume)
                                },
                                modifier = Modifier.fillMaxWidth(0.92f),
                                colors = SliderDefaults.colors(
                                    thumbColor = accentColor.copy(alpha = 0.7f),
                                    activeTrackColor = accentColor.copy(alpha = 0.5f),
                                    inactiveTrackColor = Color(0xFF141414)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Controlos de Transporte Estilo Sony (Line-Art e Play centralizado em Destaque)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { repeatMode = (repeatMode + 1) % 3 },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text(
                                    text = if (repeatMode == 2) "1" else "∞",
                                    color = if (repeatMode > 0) accentColor else Color.DarkGray,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(
                                onClick = {
                                    currentIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
                                    currentPositionMs = 0L
                                    isPlaying = true
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Text(text = "◀◀", color = Color.LightGray, fontSize = 14.sp)
                            }

                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(27.dp))
                                    .background(accentColor)
                                    .clickable { isPlaying = !isPlaying },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isPlaying) "❚❚" else "▶",
                                    color = Color.Black,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(
                                onClick = {
                                    currentIndex = if (isShuffleOn) playlist.indices.random() else (currentIndex + 1) % playlist.size
                                    currentPositionMs = 0L
                                    isPlaying = true
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Text(text = "▶▶", color = Color.LightGray, fontSize = 14.sp)
                            }

                            IconButton(
                                onClick = { isShuffleOn = !isShuffleOn },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text(
                                    text = "⤮",
                                    color = if (isShuffleOn) accentColor else Color.DarkGray,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            "info" -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AUDIO METADATA & THEME",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp
                        )
                        IconButton(onClick = { currentScreen = "player" }) {
                            Text(text = "✕", color = accentColor, fontSize = 20.sp)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0D0D0D))
                                .padding(20.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(text = "Título: ${currentTrack.title}", color = Color.White, fontSize = 14.sp)
                                Text(text = "Artista: ${currentTrack.artist}", color = Color.Gray, fontSize = 13.sp)
                                Text(text = "Duração: ${currentTrack.durationFormatted}", color = Color.Gray, fontSize = 13.sp)
                                Text(text = "Qualidade: 192 kHz / 24-bit Lossless", color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(text = "ID do Sistema: ${currentTrack.id}", color = Color.DarkGray, fontSize = 11.sp)
                            }
                        }

                        // Seletor de Tema Hi-Fi
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0D0D0D))
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(text = "COR DE DESTAQUE HI-FI", color = Color.Gray, fontSize = 11.sp, letterSpacing = 2.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    themeAccentColors.forEachIndexed { idx, color ->
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(18.dp))
                                                .background(color)
                                                .clickable { themeColorIndex = idx },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (themeColorIndex == idx) {
                                                Text(text = "✓", color = Color.Black, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { currentScreen = "player" },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(text = "VOLTAR AO PLAYER", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
            "equalizer" -> {
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
                            text = "AUDIO EQUALIZER",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp
                        )
                        IconButton(onClick = { currentScreen = "player" }) {
                            Text(text = "✕", color = accentColor, fontSize = 20.sp)
                        }
                    }

                    presets.forEachIndexed { index, name ->
                        val isSelected = index == selectedPreset
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFF151515) else Color(0xFF070707))
                                .clickable { selectedPreset = index }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                color = if (isSelected) accentColor else Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (isSelected) {
                                Text(text = "●", color = accentColor, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            "favorites" -> {
                val favoriteTracks = playlist.filter { favoriteTrackIds.value.contains(it.id) }
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
                            text = "FAVORITOS (${favoriteTracks.size})",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp
                        )
                        IconButton(onClick = { currentScreen = "player" }) {
                            Text(text = "✕", color = accentColor, fontSize = 20.sp)
                        }
                    }

                    if (favoriteTracks.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nenhuma música favorita ainda.\nToque no ícone do coração na faixa.",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(favoriteTracks) { _, track ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF070707))
                                        .clickable {
                                            val originalIndex = playlist.indexOf(track)
                                            if (originalIndex != -1) {
                                                currentIndex = originalIndex
                                                isPlaying = true
                                                currentPositionMs = 0L
                                                currentScreen = "player"
                                            }
                                        }
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth(0.8f)) {
                                        Text(
                                            text = track.title,
                                            color = accentColor,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = track.artist,
                                            color = Color.Gray,
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                    Text(
                                        text = track.durationFormatted,
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            "playlist" -> {
                val filteredPlaylist = playlist.filter { 
                    it.title.contains(searchQuery, ignoreCase = true) || 
                    it.artist.contains(searchQuery, ignoreCase = true) 
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PLAYLIST (${filteredPlaylist.size})",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp
                        )
                        IconButton(onClick = { currentScreen = "player" }) {
                            Text(text = "✕", color = accentColor, fontSize = 20.sp)
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        placeholder = { Text("Pesquisar música ou artista...", color = Color.DarkGray, fontSize = 13.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color(0xFF222222),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = accentColor
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(filteredPlaylist) { _, track ->
                            val originalIndex = playlist.indexOf(track)
                            val isSelected = originalIndex == currentIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color(0xFF151515) else Color(0xFF070707))
                                    .clickable {
                                        currentIndex = originalIndex
                                        isPlaying = true
                                        currentPositionMs = 0L
                                        currentScreen = "player"
                                    }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.fillMaxWidth(0.8f)) {
                                    Text(
                                        text = track.title,
                                        color = if (isSelected) accentColor else Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = track.artist,
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    text = track.durationFormatted,
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
