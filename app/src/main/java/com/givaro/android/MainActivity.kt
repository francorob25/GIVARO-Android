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
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
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
                GivaroApp()
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
fun GivaroApp() {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("player") } // player, equalizer, playlist, favorites, settings
    var isCircularViewMode by remember { mutableStateOf(false) } // Modo circular imersivo (5ª tela do conceito)[span_2](start_span)[span_2](end_span)
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var volume by remember { mutableFloatStateOf(0.7f) }
    var isShuffleOn by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(0) }
    
    val accentColor = Color(0xFFFF9800)
    var favoriteTrackIds by remember { mutableStateOf(setOf<Long>()) }

    var selectedPreset by remember { mutableIntStateOf(0) }
    val presets = listOf("Flat", "Bass Boost", "Clarity", "Hi-Res", "Vocal")
    val bandLevels = remember { mutableStateListOf(0.5f, 0.6f, 0.45f, 0.7f, 0.5f, 0.65f, 0.8f, 0.55f, 0.6f, 0.75f) }
    
    var bassBoostEnabled by remember { mutableStateOf(true) }
    var virtualizerEnabled by remember { mutableStateOf(false) }
    var volumeEqEnabled by remember { mutableStateOf(false) }
    var playlistCategory by remember { mutableStateOf("Todas") }
    var darkTheme by remember { mutableStateOf(true) }

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
                Track(1L, 0L, "Midnight Drive", "Givaro Sound", 202, android.net.Uri.EMPTY),
                Track(2L, 0L, "Lost in Tokyo", "Sakura Dreams", 216, android.net.Uri.EMPTY),
                Track(3L, 0L, "Beyond the Horizon", "Aurora", 268, android.net.Uri.EMPTY),
                Track(4L, 0L, "Silent Waves", "Oceanic", 201, android.net.Uri.EMPTY),
                Track(5L, 0L, "Electric Heart", "Neon Fields", 228, android.net.Uri.EMPTY)
            )
        } else {
            list
        }
    }
    
    var currentIndex by remember { mutableIntStateOf(0) }
    val currentTrack = playlist.getOrElse(currentIndex) { playlist[0] }
    val isCurrentFavorite = favoriteTrackIds.contains(currentTrack.id)

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

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF000000))) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (currentScreen) {
                    "player" -> {
                        // Tela Principal rigorosamente idêntica ao design concept (1ª tela da referência)[span_3](start_span)[span_3](end_span)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 22.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Top Bar (EQ, FAV, GIVARO, Configurações)[span_4](start_span)[span_4](end_span)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text(
                                        text = "EQ",
                                        color = accentColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.clickable { currentScreen = "equalizer" }
                                    )
                                    Text(
                                        text = "FAV",
                                        color = Color.Gray,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.clickable { currentScreen = "favorites" }
                                    )
                                }
                                Text(
                                    text = "G I V A R O",
                                    color = Color.LightGray,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Light,
                                    letterSpacing = 5.sp,
                                    modifier = Modifier.clickable { isCircularViewMode = !isCircularViewMode }
                                )
                                Text(
                                    text = "⚙",
                                    color = Color.Gray,
                                    fontSize = 18.sp,
                                    modifier = Modifier.clickable { currentScreen = "settings" }.padding(2.dp)
                                )
                            }

                            // Capa de Álbum (Suporta alternância limpa para a 5ª tela circular)[span_5](start_span)[span_5](end_span)
                            if (isCircularViewMode) {
                                Box(
                                    modifier = Modifier
                                        .size(260.dp)
                                        .clip(CircleShape)
                                        .border(2.5.dp, Brush.radialGradient(listOf(accentColor, Color.Transparent)), CircleShape)
                                        .background(Color(0xFF0A0A0A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (albumArtBitmap != null) {
                                        Image(bitmap = albumArtBitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                                            Text(text = "WAV 192 kHz", color = accentColor, fontSize = 10.sp, letterSpacing = 1.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(text = currentTrack.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(260.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF0A0A0A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (albumArtBitmap != null) {
                                        Image(bitmap = albumArtBitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                                            Text(text = "WAV 192 kHz / 24 bit", color = Color.Gray, fontSize = 10.sp, letterSpacing = 1.sp)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(text = currentTrack.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(text = currentTrack.artist, color = Color.Gray, fontSize = 13.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }

                            // Bloco Inferior Perfeitamente Proporcional
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth(0.82f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xFF221A00)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                                Text(text = "HR", color = accentColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(text = currentTrack.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = currentTrack.title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        Text(text = "Digital Music Player  •  ${currentIndex + 1}/${playlist.size}", color = Color.DarkGray, fontSize = 10.sp)
                                    }

                                    IconButton(onClick = {
                                        val set = favoriteTrackIds.toMutableSet()
                                        if (isCurrentFavorite) set.remove(currentTrack.id) else set.add(currentTrack.id)
                                        favoriteTrackIds = set
                                    }) {
                                        Text(text = if (isCurrentFavorite) "♥" else "♡", color = if (isCurrentFavorite) accentColor else Color.DarkGray, fontSize = 24.sp)
                                    }
                                }

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
                                    modifier = Modifier.height(10.dp)
                                )

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    val secs = (currentPositionMs / 1000).toInt()
                                    Text(text = String.format("%02d:%02d", secs / 60, secs % 60), color = Color.Gray, fontSize = 10.sp)
                                    Text(text = currentTrack.durationFormatted, color = Color.Gray, fontSize = 10.sp)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "VOL", color = Color.DarkGray, fontSize = 9.sp, letterSpacing = 2.sp)
                                    Slider(
                                        value = volume,
                                        onValueChange = { volume = it; mediaPlayer.setVolume(volume, volume) },
                                        modifier = Modifier.fillMaxWidth(0.9f).height(8.dp),
                                        colors = SliderDefaults.colors(thumbColor = accentColor.copy(alpha = 0.7f), activeTrackColor = accentColor.copy(alpha = 0.5f), inactiveTrackColor = Color(0xFF141414))
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF121212)).clickable { repeatMode = (repeatMode + 1) % 3 }, contentAlignment = Alignment.Center) {
                                        Text(text = if (repeatMode == 2) "1" else "∞", color = if (repeatMode > 0) accentColor else Color.DarkGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF121212)).clickable {
                                        currentIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
                                        currentPositionMs = 0L
                                        isPlaying = true
                                    }, contentAlignment = Alignment.Center) {
                                        Text(text = "◀◀", color = Color.LightGray, fontSize = 11.sp)
                                    }
                                    Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(accentColor).clickable { isPlaying = !isPlaying }, contentAlignment = Alignment.Center) {
                                        Text(text = if (isPlaying) "❚❚" else "▶", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF121212)).clickable {
                                        currentIndex = if (isShuffleOn) playlist.indices.random() else (currentIndex + 1) % playlist.size
                                        currentPositionMs = 0L
                                        isPlaying = true
                                    }, contentAlignment = Alignment.Center) {
                                        Text(text = "▶▶", color = Color.LightGray, fontSize = 11.sp)
                                    }
                                    Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF121212)).clickable { isShuffleOn = !isShuffleOn }, contentAlignment = Alignment.Center) {
                                        Text(text = "⤮", color = if (isShuffleOn) accentColor else Color.DarkGray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    "equalizer" -> {
                        // Tela 2: Equalizador (2ª tela do conceito)[span_6](start_span)[span_6](end_span)
                        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "←", color = Color.White, fontSize = 18.sp, modifier = Modifier.clickable { currentScreen = "player" })
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(text = "EQUALIZADOR", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                presets.forEachIndexed { idx, p ->
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (idx == selectedPreset) accentColor else Color(0xFF121212)).clickable { selectedPreset = idx }.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(text = p, color = if (idx == selectedPreset) Color.Black else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(modifier = Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                val freqs = listOf("60", "170", "310", "600", "1K", "3K", "6K", "12K", "14K", "16K")
                                freqs.forEachIndexed { index, freq ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .width(12.dp)
                                                .height(120.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF151515))
                                                .clickable {
                                                    bandLevels[index] = (bandLevels[index] + 0.25f).let { if (it > 1f) 0.1f else it }
                                                },
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(bandLevels[index]).background(accentColor))
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(text = freq, color = Color.Gray, fontSize = 9.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF0C0C0C)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "Reforço de graves", color = Color.White, fontSize = 13.sp)
                                    Switch(checked = bassBoostEnabled, onCheckedChange = { bassBoostEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor))
                                }
                                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF0C0C0C)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "Virtualizador de som", color = Color.White, fontSize = 13.sp)
                                    Switch(checked = virtualizerEnabled, onCheckedChange = { virtualizerEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor))
                                }
                                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF0C0C0C)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "Equalização de volume", color = Color.White, fontSize = 13.sp)
                                    Switch(checked = volumeEqEnabled, onCheckedChange = { volumeEqEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor))
                                }
                            }
                        }
                    }

                    "playlist" -> {
                        // Tela 3: Playlist (3ª tela do conceito)[span_7](start_span)[span_7](end_span)
                        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "←", color = Color.White, fontSize = 18.sp, modifier = Modifier.clickable { currentScreen = "player" })
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(text = "PLAYLIST", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                }
                                Text(text = "🔍", fontSize = 16.sp)
                            }

                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Todas", "Álbuns", "Artistas", "Pastas").forEach { cat ->
                                    val isSel = cat == playlistCategory
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (isSel) accentColor else Color(0xFF121212)).clickable { playlistCategory = cat }.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(text = cat, color = if (isSel) Color.Black else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(playlist) { idx, track ->
                                    val isSelected = idx == currentIndex
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (isSelected) Color(0xFF181200) else Color(0xFF0A0A0A)).clickable {
                                            currentIndex = idx
                                            isPlaying = true
                                            currentPositionMs = 0L
                                            currentScreen = "player"
                                        }.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth(0.75f)) {
                                            Text(text = track.title, color = if (isSelected) accentColor else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(text = track.artist, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                                        }
                                        Text(text = track.durationFormatted, color = Color.Gray, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    "favorites" -> {
                        // Tela 4: Favoritos (4ª tela do conceito)[span_8](start_span)[span_8](end_span)
                        val favList = playlist.filter { favoriteTrackIds.contains(it.id) }
                        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "←", color = Color.White, fontSize = 18.sp, modifier = Modifier.clickable { currentScreen = "player" })
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(text = "FAVORITOS", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                }
                                Text(text = "🔍", fontSize = 16.sp)
                            }

                            if (favList.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(text = "Nenhuma música favoritada", color = Color.Gray, fontSize = 13.sp)
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    itemsIndexed(favList) { _, track ->
                                        val origIdx = playlist.indexOf(track)
                                        val isSelected = origIdx == currentIndex
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (isSelected) Color(0xFF181200) else Color(0xFF0A0A0A)).clickable {
                                                currentIndex = origIdx
                                                isPlaying = true
                                                currentPositionMs = 0L
                                                currentScreen = "player"
                                            }.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth(0.75f)) {
                                                Text(text = track.title, color = if (isSelected) accentColor else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(text = track.artist, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                                            }
                                            Text(text = "♥", color = accentColor, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "settings" -> {
                        // Tela 6: Configurações (6ª tela do conceito)[span_9](start_span)[span_9](end_span)
                        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "←", color = Color.White, fontSize = 18.sp, modifier = Modifier.clickable { currentScreen = "player" })
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(text = "CONFIGURAÇÕES", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                }
                            }

                            Text(text = "APARÊNCIA", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF0A0A0A)).padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "Tema escuro", color = Color.White, fontSize = 13.sp)
                                    Switch(checked = darkTheme, onCheckedChange = { darkTheme = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF0A0A0A)).clickable {
                                        isCircularViewMode = !isCircularViewMode
                                    }.padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "Modo de visualização", color = Color.White, fontSize = 13.sp)
                                    Text(text = if (isCircularViewMode) "Imersivo >" else "Walkman >", color = accentColor, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(text = "ÁUDIO", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF0A0A0A)).padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "Qualidade de reprodução", color = Color.White, fontSize = 13.sp)
                                    Text(text = "Alta (192 kHz) >", color = Color.Gray, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(30.dp))

                            Text(text = "SOBRE", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF0A0A0A)).padding(16.dp)) {
                                Column {
                                    Text(text = "GIVARO", color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                    Text(text = "v1.0.0 • Digital High-Res Player", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Barra de Navegação Inferior Fixa
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF030303)).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { currentScreen = "player" }) {
                    Text(text = "🎵", color = if (currentScreen == "player") accentColor else Color.Gray, fontSize = 20.sp)
                }
                IconButton(onClick = { currentScreen = "equalizer" }) {
                    Text(text = "⏱", color = if (currentScreen == "equalizer") accentColor else Color.Gray, fontSize = 20.sp)
                }
                IconButton(onClick = { currentScreen = "playlist" }) {
                    Text(text = "☰", color = if (currentScreen == "playlist") accentColor else Color.Gray, fontSize = 22.sp)
                }
                IconButton(onClick = { currentScreen = "favorites" }) {
                    Text(text = "♥", color = if (currentScreen == "favorites") accentColor else Color.Gray, fontSize = 20.sp)
                }
            }
        }
    }
}

