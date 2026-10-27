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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val title: String,
    val artist: String,
    val duration: Long,
    val uri: android.net.Uri
)

val Amber = Color(0xFFFF9800)
val Muted = Color(0xFF888888)
val Panel = Color(0xFF121212)
val Selected = Color(0xFF1E1A14)

@Composable
fun GivaroApp() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf("player") }
    var playing by remember { mutableStateOf(false) }
    var index by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var volume by remember { mutableFloatStateOf(0.7f) }
    var shuffle by remember { mutableStateOf(false) }
    var repeatOne by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var preset by remember { mutableIntStateOf(0) }
    val presets = listOf("Flat", "Bass Boost", "Clarity", "Hi-Res", "Vocal")
    
    var favorites by remember { mutableStateOf(setOf<Long>()) }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    Manifest.permission.READ_MEDIA_AUDIO
                else
                    Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_AUDIO
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
    }

    val tracks = remember(permissionGranted) {
        val list = mutableListOf<Track>()
        if (permissionGranted) {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION
            )
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                MediaStore.Audio.Media.TITLE + " ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Desconhecido"
                    val artist = cursor.getString(artistCol) ?: "Artista Desconhecido"
                    val dur = cursor.getLong(durCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    list.add(Track(id, title, artist, dur, uri))
                }
            }
        }
        list
    }

    val track = tracks.getOrNull(index)
    val duration = track?.duration ?: 0L

    val player = remember { MediaPlayer() }

    LaunchedEffect(track, playing) {
        if (track != null) {
            try {
                if (playing) {
                    if (!player.isPlaying) {
                        try {
                            player.reset()
                            player.setDataSource(context, track.uri)
                            player.prepare()
                            player.seekTo(position.toInt())
                            player.start()
                        } catch (_: Exception) {
                            player.start()
                        }
                    }
                } else {
                    if (player.isPlaying) {
                        player.pause()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(playing) {
        while (playing) {
            delay(300L)
            try {
                if (player.isPlaying) {
                    position = player.currentPosition.toLong()
                    if (duration > 0 && position >= duration - 500) {
                        if (repeatOne) {
                            player.seekTo(0)
                            position = 0L
                        } else {
                            index = if (shuffle && tracks.size > 1) {
                                (tracks.indices.filter { it != index }).random()
                            } else {
                                (index + 1) % tracks.size
                            }
                            position = 0L
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(volume) {
        try {
            player.setVolume(volume, volume)
        } catch (_: Exception) {}
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                player.release()
            } catch (_: Exception) {}
        }
    }

    fun selectPreset(i: Int) {
        preset = i
    }

    fun timeText(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format("%02d:%02d", m, s)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        screen = if (screen == "equalizer") "player"
                        else "equalizer"
                    }
                ) {
                    Text("EQ", color = Amber, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "GIVARO",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 7.sp
                    )
                    Text(
                        "PURE SOUND",
                        color = Muted,
                        fontSize = 8.sp,
                        letterSpacing = 3.sp
                    )
                }

                TextButton(
                    onClick = {
                        screen = if (screen == "playlist") "player"
                        else "playlist"
                    }
                ) {
                    Text("☰", color = Amber, fontSize = 22.sp)
                }
            }

            Spacer(Modifier.height(18.dp))

            when (screen) {
                "equalizer" -> {
                    Text(
                        "AUDIO EQUALIZER",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Personalize sua experiência sonora",
                        color = Muted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(24.dp))

                    presets.forEachIndexed { i, name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (preset == i) Selected else Panel
                                )
                                .clickable { selectPreset(i) }
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                name,
                                color = if (preset == i) Amber else Color.White,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (preset == i) "●" else "›",
                                color = Amber
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Os efeitos disponíveis dependem do aparelho.",
                        color = Muted,
                        fontSize = 11.sp
                    )
                }

                "playlist" -> {
                    Text(
                        "SUA BIBLIOTECA",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text("Buscar música ou artista", color = Muted)
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Amber,
                            unfocusedBorderColor = Selected,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Amber
                        )
                    )

                    Spacer(Modifier.height(12.dp))

                    if (!permissionGranted) {
                        Button(
                            onClick = {
                                permissionLauncher.launch(permission)
                            }
                        ) {
                            Text("Permitir acesso às músicas")
                        }
                    } else if (tracks.isEmpty()) {
                        Text(
                            "Nenhuma música encontrada no dispositivo.",
                            color = Muted
                        )
                    } else {
                        val filtered = tracks.filter {
                            it.title.contains(query, true) ||
                                it.artist.contains(query, true)
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filtered, key = { it.id }) { item ->
                                val originalIndex =
                                    tracks.indexOfFirst { it.id == item.id }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(
                                            if (originalIndex == index)
                                                Selected else Panel
                                        )
                                        .clickable {
                                            index = originalIndex
                                            position = 0L
                                            playing = true
                                            screen = "player"
                                        }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF30200D)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("♫", color = Amber, fontSize = 23.sp)
                                    }

                                    Spacer(Modifier.width(12.dp))

                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            item.title,
                                            color = if (originalIndex == index)
                                                Amber else Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            item.artist,
                                            color = Muted,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Text(
                                        timeText(item.duration),
                                        color = Muted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    Spacer(Modifier.weight(1f))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF38220A),
                                        Color(0xFF15191B),
                                        Color(0xFF080A0B)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .size(130.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF251805)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "G",
                                    color = Amber,
                                    fontSize = 78.sp,
                                    fontWeight = FontWeight.Light
                                )
                            }

                            Spacer(Modifier.height(20.dp))

                            Text(
                                "G I V A R O",
                                color = Color.White,
                                fontSize = 16.sp,
                                letterSpacing = 5.sp
                            )

                            Text(
                                "PREMIUM AUDIO",
                                color = Muted,
                                fontSize = 9.sp,
                                letterSpacing = 3.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    if (!permissionGranted) {
                        Button(
                            onClick = {
                                permissionLauncher.launch(permission)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Amber
                            )
                        ) {
                            Text(
                                "Permitir acesso às músicas",
                                color = Color.Black
                            )
                        }
                    }

                    Text(
                        track?.title ?: "Nenhuma música selecionada",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(5.dp))

                    Text(
                        track?.artist ?: "Sua música, sua experiência",
                        color = Muted,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(16.dp))

                    Slider(
                        value = if (duration > 0)
                            (position.toFloat() / duration).coerceIn(0f, 1f)
                        else 0f,
                        onValueChange = { fraction ->
                            position = (fraction * duration).toLong()
                            try {
                                player.seekTo(position.toInt())
                            } catch (_: Exception) {}
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Amber,
                            activeTrackColor = Amber,
                            inactiveTrackColor = Selected
                        )
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(timeText(position), color = Muted, fontSize = 11.sp)
                        Text(timeText(duration), color = Muted, fontSize = 11.sp)
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "VOL",
                            color = Muted,
                            fontSize = 10.sp,
                            letterSpacing = 2.sp
                        )

                        Slider(
                            value = volume,
                            onValueChange = { volume = it },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Amber,
                                activeTrackColor = Amber,
                                inactiveTrackColor = Selected
                            )
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { shuffle = !shuffle }) {
                            Text(
                                "⤮",
                                color = if (shuffle) Amber else Muted,
                                fontSize = 22.sp
                            )
                        }

                        PlayerButton("◀◀") {
                            if (tracks.isNotEmpty()) {
                                index = if (index > 0) index - 1
                                else tracks.lastIndex
                                position = 0L
                                playing = true
                            }
                        }

                        Box(
                            Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(Amber)
                                .clickable {
                                    if (tracks.isEmpty()) {
                                        screen = "playlist"
                                    } else {
                                        playing = !playing
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (playing) "Ⅱ" else "▶",
                                color = Color.Black,
                                fontSize = 27.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        PlayerButton("▶▶") {
                            if (tracks.isNotEmpty()) {
                                index = if (shuffle && tracks.size > 1) {
                                    (tracks.indices.filter { it != index }).random()
                                } else {
                                    (index + 1) % tracks.size
                                }
                                position = 0L
                                playing = true
                            }
                        }

                        TextButton(onClick = { repeatOne = !repeatOne }) {
                            Text(
                                if (repeatOne) "1" else "↻",
                                color = if (repeatOne) Amber else Muted,
                                fontSize = 22.sp
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = {
                                track?.let {
                                    favorites = if (it.id in favorites)
                                        favorites - it.id
                                    else favorites + it.id
                                }
                            }
                        ) {
                            Text(
                                if (track != null && track.id in favorites)
                                    "♥  FAVORITO"
                                else "♡  ADICIONAR AOS FAVORITOS",
                                color = if (track != null && track.id in favorites)
                                    Amber else Muted,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerButton(
    label: String,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Panel)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
