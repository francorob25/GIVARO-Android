package com.givaro.android

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

private val Amber = Color(0xFFFFA726)
private val Black = Color(0xFF08090C)
private val Panel = Color(0xFF15171C)
private val Muted = Color(0xFF9297A1)
private val White = Color(0xFFF5F5F7)

private data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val uri: Uri
)

data class Service(
    val name: String,
    val url: String,
    val screenKey: String
)

private val services = listOf(
    Service("Apple Music", "https://music.apple.com", "apple"),
    Service("Spotify", "https://open.spotify.com", "spotify"),
    Service("Deezer", "https://www.deezer.com", "deezer"),
    Service("Amazon Music", "https://music.amazon.com", "amazon"),
    Service("YouTube Music", "https://music.youtube.com", "ytmusic"),
    Service("YouTube", "https://m.youtube.com", "youtube")
)

class MainActivity : ComponentActivity() {

    private val audioPermission: String
        get() = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private val permissionRequest =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            Toast.makeText(
                this,
                if (granted) "Permissão autorizada." else "Permissão necessária.",
                Toast.LENGTH_LONG
            ).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.rgb(8, 9, 12)
        window.navigationBarColor = android.graphics.Color.rgb(8, 9, 12)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Amber,
                    secondary = Amber,
                    background = Black,
                    surface = Panel,
                    onBackground = White,
                    onSurface = White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Black
                ) {
                    GivaroScreen(
                        hasPermission = {
                            ContextCompat.checkSelfPermission(
                                this,
                                audioPermission
                            ) == PackageManager.PERMISSION_GRANTED
                        },
                        requestPermission = {
                            permissionRequest.launch(audioPermission)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GivaroScreen(
    hasPermission: () -> Boolean,
    requestPermission: () -> Unit
) {
    val context = LocalContext.current
    val tracks = remember { mutableStateListOf<Track>() }

    var selected by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.8f) }
    var position by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableIntStateOf(0) }
    var screen by remember { mutableStateOf("player") }
    var selectedServiceKey by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var loadKey by remember { mutableIntStateOf(0) }
    var isFavorite by remember { mutableStateOf(false) }

    val track = tracks.getOrNull(selected)
    val selectedService = services.find { it.screenKey == selectedServiceKey }

    fun refreshTracks() {
        if (!hasPermission()) {
            requestPermission()
            return
        }

        tracks.clear()

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )

        try {
            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { cursor ->

                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val title = cursor.getString(titleCol) ?: "Faixa sem título"
                    val id = cursor.getLong(idCol)

                    tracks.add(
                        Track(
                            id = id,
                            title = title,
                            artist = cursor.getString(artistCol)
                                ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                                ?: "Artista desconhecido",
                            duration = cursor.getLong(durationCol),
                            uri = ContentUris.withAppendedId(collection, id)
                        )
                    )
                }
            }

            selected = selected.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
            loadKey++

            Toast.makeText(
                context,
                "${tracks.size} músicas encontradas",
                Toast.LENGTH_SHORT
            ).show()

        } catch (_: Exception) {
            Toast.makeText(context, "Erro ao carregar músicas.", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                player?.release()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(Unit) {
        if (hasPermission()) refreshTracks()
    }

    LaunchedEffect(selected, loadKey) {
        val current = tracks.getOrNull(selected)

        if (current == null) {
            playing = false
            position = 0f
            duration = 0
            return@LaunchedEffect
        }

        try {
            player?.release()

            val mp = MediaPlayer()
            player = mp
            mp.setDataSource(context, current.uri)

            mp.setOnPreparedListener { ready ->
                duration = ready.duration
                ready.setVolume(volume, volume)
                if (playing) ready.start()
            }

            mp.setOnCompletionListener {
                if (repeat) {
                    it.seekTo(0)
                    it.start()
                } else if (tracks.isNotEmpty()) {
                    selected = if (shuffle && tracks.size > 1) {
                        tracks.indices.filter { index -> index != selected }.random()
                    } else {
                        (selected + 1) % tracks.size
                    }
                    playing = true
                }
            }

            mp.prepareAsync()

        } catch (_: Exception) {
            playing = false
            Toast.makeText(context, "Não foi possível reproduzir.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(playing) {
        try {
            val mp = player
            if (mp != null) {
                if (playing && !mp.isPlaying) mp.start()
                else if (!playing && mp.isPlaying) mp.pause()
            }
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(playing, selected) {
        while (playing) {
            try {
                player?.let {
                    if (it.isPlaying) {
                        position = it.currentPosition.toFloat()
                        duration = it.duration
                    }
                }
            } catch (_: Exception) {
            }
            delay(400)
        }
    }

    LaunchedEffect(volume) {
        try {
            player?.setVolume(volume, volume)
        } catch (_: Exception) {
        }
    }

    fun next() {
        if (tracks.isEmpty()) {
            refreshTracks()
            return
        }

        selected = if (shuffle && tracks.size > 1) {
            tracks.indices.filter { it != selected }.random()
        } else {
            (selected + 1) % tracks.size
        }
        playing = true
    }

    fun previous() {
        if (tracks.isEmpty()) return

        try {
            if ((player?.currentPosition ?: 0) > 3000) {
                player?.seekTo(0)
                position = 0f
            } else {
                selected = if (selected > 0) selected - 1 else tracks.lastIndex
                playing = true
            }
        } catch (_: Exception) {
            selected = if (selected > 0) selected - 1 else tracks.lastIndex
            playing = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "♫",
                color = Amber,
                fontSize = 25.sp,
                modifier = Modifier.clickable { screen = "equalizer" }
            )

            Text(
                "GIVARO",
                color = White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )

            Text(
                "⋮",
                color = White,
                fontSize = 28.sp,
                modifier = Modifier.clickable { screen = "menu" }
            )
        }

        when {
            selectedService != null -> {
                StreamingWebViewScreen(
                    service = selectedService,
                    onBack = { selectedServiceKey = null }
                )
            }

            screen == "player" -> {
                PlayerScreen(
                    track = track,
                    playing = playing,
                    shuffle = shuffle,
                    repeat = repeat,
                    favorite = isFavorite,
                    position = position,
                    duration = duration,
                    volume = volume,
                    onPlay = { playing = !playing },
                    onNext = { next() },
                    onPrevious = { previous() },
                    onShuffle = { shuffle = !shuffle },
                    onRepeat = { repeat = !repeat },
                    onFavorite = { isFavorite = !isFavorite },
                    onSeek = {
                        try {
                            player?.seekTo(it.toInt())
                            position = it
                        } catch (_: Exception) {
                        }
                    },
                    onVolume = { volume = it },
                    onService = { selectedServiceKey = it }
                )
            }

            screen == "library" -> {
                LibraryScreen(
                    tracks = tracks,
                    selected = selected,
                    onSelect = {
                        selected = it
                        playing = true
                        screen = "player"
                    },
                    onRefresh = { refreshTracks() }
                )
            }

            screen == "equalizer" -> {
                EqualizerScreen()
            }

            screen == "menu" -> {
                MenuScreen(
                    onNavigate = { screen = it },
                    onRefresh = { refreshTracks() }
                )
            }

            screen == "settings" -> {
                SettingsScreen()
            }
        }

        Spacer(Modifier.weight(1f))

        NavigationBar(
            screen = screen,
            onNavigate = {
                selectedServiceKey = null
                screen = it
            }
        )
    }
}

@Composable
private fun PlayerScreen(
    track: Track?,
    playing: Boolean,
    shuffle: Boolean,
    repeat: Boolean,
    favorite: Boolean,
    position: Float,
    duration: Int,
    volume: Float,
    onPlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onFavorite: () -> Unit,
    onSeek: (Float) -> Unit,
    onVolume: (Float) -> Unit,
    onService: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF292D36),
                            Color(0xFF101116),
                            Color(0xFF34302A)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("G", color = Amber, fontSize = 100.sp, fontWeight = FontWeight.Black)
                Text("G I V A R O", color = White, fontSize = 15.sp, letterSpacing = 5.sp)
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track?.title ?: "Nenhuma música selecionada",
                    color = White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track?.artist ?: "Sua música começa aqui",
                    color = Muted,
                    fontSize = 14.sp
                )
            }

            Text(
                if (favorite) "♥" else "♡",
                color = if (favorite) Amber else White,
                fontSize = 28.sp,
                modifier = Modifier.clickable { onFavorite() }
            )
        }

        Spacer(Modifier.height(18.dp))

        Slider(
            value = position.coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
            onValueChange = onSeek,
            valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Amber,
                activeTrackColor = Amber,
                inactiveTrackColor = Panel
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(timeText(position.toInt()), color = Muted, fontSize = 12.sp)
            Text(timeText(duration), color = Muted, fontSize = 12.sp)
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlText("⇄", shuffle, onShuffle)
            ControlText("|◀", false, onPrevious)

            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Amber)
                    .clickable { onPlay() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (playing) "Ⅱ" else "▶",
                    color = Black,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            ControlText("▶|", false, onNext)
            ControlText("↻", repeat, onRepeat)
        }

        Spacer(Modifier.height(18.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("◖", color = Muted)
            Slider(
                modifier = Modifier.weight(1f),
                value = volume,
                onValueChange = onVolume,
                colors = SliderDefaults.colors(
                    thumbColor = Amber,
                    activeTrackColor = Amber
                )
            )
            Text("◗", color = Muted)
        }

        Spacer(Modifier.height(22.dp))

        Text(
            "STREAMING",
            color = Muted,
            fontSize = 11.sp,
            letterSpacing = 3.sp,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            services.forEach { service ->
                StreamingIcon(
                    service = service,
                    onClick = { onService(service.screenKey) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ControlText(
    symbol: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Text(
        symbol,
        color = if (active) Amber else White,
        fontSize = 23.sp,
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
private fun StreamingIcon(
    service: Service,
    onClick: () -> Unit
) {
    val color = when (service.screenKey) {
        "spotify" -> Color(0xFF1DB954)
        "apple" -> Color(0xFFFA4565)
        "deezer" -> Color(0xFF8B5CF6)
        "amazon" -> Color(0xFF40B9E8)
        "ytmusic" -> Color(0xFFFF3344)
        else -> Color(0xFFFF3333)
    }

    val symbol = when (service.screenKey) {
        "spotify" -> "≋"
        "apple" -> "♫"
        "deezer" -> "▥"
        "amazon" -> "a"
        "ytmusic" -> "▶"
        else -> "▶"
    }

    Column(
        modifier = Modifier
            .width(68.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                symbol,
                color = Color.White,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(7.dp))

        Text(
            service.name,
            color = Muted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun LibraryScreen(
    tracks: List<Track>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Biblioteca", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(
                "↻",
                color = Amber,
                fontSize = 25.sp,
                modifier = Modifier.clickable { onRefresh() }
            )
        }

        Text("${tracks.size} músicas", color = Muted, fontSize = 13.sp)

        Spacer(Modifier.height(16.dp))

        if (tracks.isEmpty()) {
            Text(
                "Nenhuma música encontrada.\nToque em atualizar para procurar arquivos.",
                color = Muted,
                modifier = Modifier.padding(20.dp)
            )
        } else {
            LazyColumn {
                itemsIndexed(tracks) { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Panel),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("♫", color = Amber, fontSize = 22.sp)
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        ) {
                            Text(
                                item.title,
                                color = if (index == selected) Amber else White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(item.artist, color = Muted, fontSize = 12.sp)
                        }

                        Text(
                            timeText(item.duration.toInt()),
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }

                    HorizontalDivider(color = Panel)
                }
            }
        }
    }
}

@Composable
private fun EqualizerScreen() {
    var enabled by remember { mutableStateOf(true) }
    val bands = remember { mutableStateListOf(0f, 0f, 0f, 0f, 0f) }
    val names = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("Equalizador", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Equalizador", color = White)
            Switch(
                checked = enabled,
                onCheckedChange = { enabled = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Amber)
            )
        }

        Spacer(Modifier.height(25.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bands.forEachIndexed { index, value ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${value.toInt()}",
                        color = Amber,
                        fontSize = 11.sp
                    )

                    Slider(
                        value = value,
                        onValueChange = { bands[index] = it },
                        valueRange = -10f..10f,
                        enabled = enabled,
                        modifier = Modifier
                            .height(190.dp)
                            .width(48.dp)
                    )

                    Text(
                        names[index],
                        color = Muted,
                        fontSize = 9.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "Os controles visuais ainda não estão ligados ao processamento de áudio.",
            color = Muted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun MenuScreen(
    onNavigate: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Menu", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(20.dp))

        MenuRow("♫", "Biblioteca") { onNavigate("library") }
        MenuRow("◉", "Equalizador") { onNavigate("equalizer") }
        MenuRow("⚙", "Configurações") { onNavigate("settings") }
        MenuRow("↻", "Atualizar músicas") { onRefresh() }
        MenuRow("▶", "Reprodutor") { onNavigate("player") }
    }
}

@Composable
private fun SettingsScreen() {
    var walkman by remember { mutableStateOf(false) }
    var privateMode by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Configurações", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Modo Walkman", color = White)
                Text("Interface de reprodução", color = Muted, fontSize = 12.sp)
            }
            Switch(
                checked = walkman,
                onCheckedChange = { walkman = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Amber)
            )
        }

        HorizontalDivider(color = Panel)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Modo privado", color = White)
                Text("Preferências de privacidade", color = Muted, fontSize = 12.sp)
            }
            Switch(
                checked = privateMode,
                onCheckedChange = { privateMode = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Amber)
            )
        }

        Spacer(Modifier.height(25.dp))

        Text("GIVARO", color = Amber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Versão inicial", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun MenuRow(
    symbol: String,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(symbol, color = Amber, fontSize = 23.sp)
        Text(
            title,
            color = White,
            fontSize = 16.sp,
            modifier = Modifier.padding(start = 16.dp)
        )
        Spacer(Modifier.weight(1f))
        Text("›", color = Muted, fontSize = 25.sp)
    }

    HorizontalDivider(color = Panel)
}

@Composable
private fun NavigationBar(
    screen: String,
    onNavigate: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem("⌂", "Início", screen == "player") { onNavigate("player") }
        NavItem("♫", "Biblioteca", screen == "library") { onNavigate("library") }
        NavItem("≋", "EQ", screen == "equalizer") { onNavigate("equalizer") }
        NavItem("☰", "Menu", screen == "menu" || screen == "settings") { onNavigate("menu") }
    }
}

@Composable
private fun NavItem(
    symbol: String,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            symbol,
            color = if (active) Amber else Muted,
            fontSize = 22.sp
        )
        Text(
            label,
            color = if (active) Amber else Muted,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun StreamingWebViewScreen(
    service: Service,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "‹",
                color = Amber,
                fontSize = 30.sp,
                modifier = Modifier.clickable { onBack() }
            )
            Text(
                service.name,
                color = White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.mixedContentMode =
                        WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)

                    CookieManager.getInstance().setAcceptCookie(true)

                    webViewClient = object : WebViewClient() {
                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: android.net.http.SslError?
                        ) {
                            handler?.cancel()
                        }
                    }

                    loadUrl(service.url)
                }
            }
        )
    }
}

private fun timeText(milliseconds: Int): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
