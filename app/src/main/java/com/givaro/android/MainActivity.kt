package com.givaro.android

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.SslErrorHandler
import android.net.http.SslError
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.random.Random

private val Background = Color(0xFF08090C)
private val Panel = Color(0xFF15181E)
private val PanelLight = Color(0xFF252A33)
private val Orange = Color(0xFFFF9418)
private val White = Color(0xFFF5F5F7)
private val Muted = Color(0xFF9297A1)

data class MusicTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri,
    val duration: Long
)

enum class GivaroTab {
    HOME, LIBRARY, EQUALIZER, MENU
}

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            showApp()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(permission)
        } else {
            showApp()
        }
    }

    private fun showApp() {
        setContent {
            GivaroApp()
        }
    }
}

@Composable
fun GivaroApp() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Orange,
            background = Background,
            surface = Panel,
            onPrimary = Color.Black,
            onBackground = White,
            onSurface = White
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Background
        ) {
            GivaroScreen()
        }
    }
}

@Composable
fun GivaroScreen() {
    val context = LocalContext.current

    val tracks = remember { mutableStateListOf<MusicTrack>() }

    var selectedIndex by remember { mutableIntStateOf(0) }
    var tab by remember { mutableStateOf(GivaroTab.HOME) }
    var playing by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    var volume by remember { mutableFloatStateOf(0.8f) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var service by remember { mutableStateOf<String?>(null) }
    var walkman by remember { mutableStateOf(false) }
    var privateMode by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(setOf<Long>()) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    val currentTrack = tracks.getOrNull(selectedIndex)

    fun loadTracks() {
        tracks.clear()

        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(context, permission)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
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
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)

                    tracks.add(
                        MusicTrack(
                            id = id,
                            title = cursor.getString(titleCol) ?: "Música desconhecida",
                            artist = cursor.getString(artistCol) ?: "Artista desconhecido",
                            album = cursor.getString(albumCol) ?: "Álbum desconhecido",
                            uri = Uri.withAppendedPath(collection, id.toString()),
                            duration = cursor.getLong(durationCol)
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }

        if (selectedIndex >= tracks.size) selectedIndex = 0
    }

    fun playTrack(index: Int) {
        if (tracks.isEmpty()) return

        val safeIndex = index.coerceIn(0, tracks.lastIndex)
        selectedIndex = safeIndex
        position = 0L
        prepared = false

        try {
            player?.release()

            val newPlayer = MediaPlayer()
            player = newPlayer

            newPlayer.setDataSource(context, tracks[safeIndex].uri)

            newPlayer.setOnPreparedListener { mp ->
                prepared = true
                duration = mp.duration.toLong().coerceAtLeast(1L)
                mp.setVolume(volume, volume)

                if (playing) mp.start()
            }

            newPlayer.setOnCompletionListener {
                if (repeat) {
                    it.seekTo(0)
                    it.start()
                } else {
                    val next = if (shuffle && tracks.size > 1) {
                        Random.nextInt(tracks.size)
                    } else {
                        (selectedIndex + 1) % tracks.size
                    }

                    playing = true
                    playTrack(next)
                }
            }

            newPlayer.prepareAsync()

        } catch (_: Exception) {
            playing = false
            prepared = false
        }
    }

    fun togglePlayback() {
        if (tracks.isEmpty()) return

        if (player == null) {
            playing = true
            playTrack(selectedIndex)
        } else if (prepared) {
            if (playing) {
                player?.pause()
                playing = false
            } else {
                player?.start()
                playing = true
            }
        } else {
            playing = !playing
        }
    }

    fun nextTrack() {
        if (tracks.isEmpty()) return

        val next = if (shuffle && tracks.size > 1) {
            Random.nextInt(tracks.size)
        } else {
            (selectedIndex + 1) % tracks.size
        }

        playing = true
        playTrack(next)
    }

    fun previousTrack() {
        if (tracks.isEmpty()) return

        if (position > 3000L) {
            try {
                player?.seekTo(0)
            } catch (_: Exception) {
            }
        } else {
            val previous = if (selectedIndex == 0) {
                tracks.lastIndex
            } else {
                selectedIndex - 1
            }

            playing = true
            playTrack(previous)
        }
    }

    LaunchedEffect(Unit) {
        loadTracks()
    }

    LaunchedEffect(playing, prepared, selectedIndex) {
        while (playing && prepared) {
            try {
                position = player?.currentPosition?.toLong() ?: 0L
                duration = player?.duration?.toLong()?.coerceAtLeast(1L) ?: 1L
            } catch (_: Exception) {
            }
            delay(500)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                player?.release()
            } catch (_: Exception) {
            }
            player = null
        }
    }

    if (service != null) {
        StreamingScreen(
            service = service!!,
            onClose = { service = null }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .systemBarsPadding()
    ) {
        Header(
            onEqualizer = { tab = GivaroTab.EQUALIZER },
            onMenu = { tab = GivaroTab.MENU }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (tab) {
                GivaroTab.HOME -> HomeScreen(
                    track = currentTrack,
                    playing = playing,
                    position = position,
                    duration = duration,
                    volume = volume,
                    shuffle = shuffle,
                    repeat = repeat,
                    favorite = currentTrack?.id in favorites,
                    onPlay = { togglePlayback() },
                    onNext = { nextTrack() },
                    onPrevious = { previousTrack() },
                    onShuffle = { shuffle = !shuffle },
                    onRepeat = { repeat = !repeat },
                    onVolume = {
                        volume = it
                        try {
                            player?.setVolume(it, it)
                        } catch (_: Exception) {
                        }
                    },
                    onSeek = {
                        position = it
                        if (prepared) {
                            try {
                                player?.seekTo(it.toInt())
                            } catch (_: Exception) {
                            }
                        }
                    },
                    onFavorite = {
                        currentTrack?.let { track ->
                            favorites = if (track.id in favorites) {
                                favorites - track.id
                            } else {
                                favorites + track.id
                            }
                        }
                    },
                    onLibrary = { tab = GivaroTab.LIBRARY },
                    onService = { service = it }
                )

                GivaroTab.LIBRARY -> LibraryScreen(
                    tracks = tracks,
                    selectedIndex = selectedIndex,
                    favorites = favorites,
                    search = search,
                    onSearch = { search = it },
                    onRefresh = { loadTracks() },
                    onSelect = {
                        playing = true
                        playTrack(it)
                        tab = GivaroTab.HOME
                    }
                )

                GivaroTab.EQUALIZER -> EqualizerScreen()

                GivaroTab.MENU -> MenuScreen(
                    walkman = walkman,
                    privateMode = privateMode,
                    onWalkman = { walkman = it },
                    onPrivate = { privateMode = it },
                    onLibrary = { tab = GivaroTab.LIBRARY },
                    onHome = { tab = GivaroTab.HOME },
                    onEqualizer = { tab = GivaroTab.EQUALIZER }
                )
            }
        }

        BottomNavigation(
            selected = tab,
            onSelect = { tab = it }
        )
    }
}

@Composable
fun Header(
    onEqualizer: () -> Unit,
    onMenu: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Orange),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "G",
                color = Color.Black,
                fontSize = 25.sp,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(Modifier.width(11.dp))

        Text(
            "GIVARO",
            color = White,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 3.sp
        )

        Spacer(Modifier.weight(1f))

        TextButton(onClick = onEqualizer) {
            Text("EQ", color = Orange, fontWeight = FontWeight.Bold)
        }

        TextButton(onClick = onMenu) {
            Text("•••", color = White, fontSize = 20.sp)
        }
    }
}

@Composable
fun HomeScreen(
    track: MusicTrack?,
    playing: Boolean,
    position: Long,
    duration: Long,
    volume: Float,
    shuffle: Boolean,
    repeat: Boolean,
    favorite: Boolean,
    onPlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onVolume: (Float) -> Unit,
    onSeek: (Long) -> Unit,
    onFavorite: () -> Unit,
    onLibrary: () -> Unit,
    onService: (String) -> Unit
) {
    val services = listOf(
        "Spotify",
        "YouTube Music",
        "Apple Music",
        "Deezer",
        "Amazon Music",
        "YouTube"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "REPRODUZINDO",
                    color = Orange,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    "Sua música. Seu espaço.",
                    color = Muted,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.weight(1f))

            TextButton(onClick = onLibrary) {
                Text("BIBLIOTECA  ›", color = Orange, fontSize = 10.sp)
            }
        }

        AlbumArtwork(
            track = track,
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(1f)
        )

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track?.title ?: "Escolha uma música",
                    color = White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    track?.artist ?: "Sua biblioteca está esperando",
                    color = Muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            TextButton(onClick = onFavorite) {
                Text(
                    if (favorite) "♥" else "♡",
                    color = if (favorite) Orange else Muted,
                    fontSize = 25.sp
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Slider(
            value = position.toFloat().coerceIn(
                0f,
                duration.toFloat().coerceAtLeast(1f)
            ),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = Orange,
                activeTrackColor = Orange,
                inactiveTrackColor = PanelLight
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(position), color = Muted, fontSize = 10.sp)
            Text(formatTime(duration), color = Muted, fontSize = 10.sp)
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onShuffle) {
                Text(
                    "⤨",
                    color = if (shuffle) Orange else Muted,
                    fontSize = 23.sp
                )
            }

            TextButton(onClick = onPrevious) {
                Text("◀|", color = White, fontSize = 23.sp)
            }

            Box(
                modifier = Modifier
                    .size(62.dp)
                    .shadow(10.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Orange),
                contentAlignment = Alignment.Center
            ) {
                TextButton(onClick = onPlay) {
                    Text(
                        if (playing) "Ⅱ" else "▶",
                        color = Color.Black,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            TextButton(onClick = onNext) {
                Text("|▶", color = White, fontSize = 23.sp)
            }

            TextButton(onClick = onRepeat) {
                Text(
                    "↻",
                    color = if (repeat) Orange else Muted,
                    fontSize = 23.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("−", color = Muted, fontSize = 19.sp)

            Slider(
                value = volume,
                onValueChange = onVolume,
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Orange,
                    activeTrackColor = Orange,
                    inactiveTrackColor = PanelLight
                )
            )

            Text("+", color = Muted, fontSize = 19.sp)
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "SERVIÇOS DE MÚSICA",
                color = Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )

            Spacer(Modifier.weight(1f))

            Text("ABRIR", color = Orange, fontSize = 9.sp)
        }

        Spacer(Modifier.height(10.dp))

        services.chunked(3).forEach { rowServices ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                rowServices.forEach { name ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Panel)
                            .clickable { onService(name) }
                            .padding(7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            when (name) {
                                "Spotify" -> "S"
                                "YouTube Music" -> "▶"
                                "Apple Music" -> "♫"
                                "Deezer" -> "D"
                                "Amazon Music" -> "a"
                                else -> "▶"
                            },
                            color = Orange,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            name,
                            color = White,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                repeat(3 - rowServices.size) {
                    Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(15.dp))
    }
}

@Composable
fun AlbumArtwork(
    track: MusicTrack?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = track?.uri
    ) {
        value = null

        val uri = track?.uri ?: return@produceState

        value = withContext(Dispatchers.IO) {
            var retriever: MediaMetadataRetriever? = null

            try {
                retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)

                val bytes = retriever.embeddedPicture

                if (bytes != null) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            } finally {
                try {
                    retriever?.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF343A45),
                        Color(0xFF171B22),
                        Color(0xFF08090C)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Capa do álbum",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                "G",
                color = Orange,
                fontSize = 112.sp,
                fontWeight = FontWeight.Black
            )
        }

        Text(
            "G",
            color = Color.White.copy(alpha = 0.12f),
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(13.dp)
        )
    }
}

@Composable
fun LibraryScreen(
    tracks: List<MusicTrack>,
    selectedIndex: Int,
    favorites: Set<Long>,
    search: String,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val filtered = tracks.withIndex().filter { item ->
        item.value.title.contains(search, true) ||
                item.value.artist.contains(search, true) ||
                item.value.album.contains(search, true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Biblioteca",
                    color = White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "${tracks.size} músicas no dispositivo",
                    color = Muted,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.weight(1f))

            TextButton(onClick = onRefresh) {
                Text("ATUALIZAR", color = Orange, fontSize = 10.sp)
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("Buscar música ou artista", color = Muted, fontSize = 13.sp)
            },
            leadingIcon = {
                Text("⌕", color = Muted, fontSize = 23.sp)
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Orange,
                unfocusedBorderColor = PanelLight,
                focusedTextColor = White,
                unfocusedTextColor = White,
                cursorColor = Orange
            )
        )

        Spacer(Modifier.height(14.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("♫", color = Orange, fontSize = 42.sp)

                    Text(
                        if (tracks.isEmpty()) {
                            "Nenhuma música encontrada"
                        } else {
                            "Nenhum resultado"
                        },
                        color = White,
                        fontSize = 15.sp
                    )

                    Text(
                        "Adicione músicas ao dispositivo e atualize a biblioteca.",
                        color = Muted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(bottom = 18.dp)
            ) {
                items(filtered) { item ->
                    val index = item.index
                    val track = item.value

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                if (index == selectedIndex) PanelLight else Panel
                            )
                            .clickable { onSelect(index) }
                            .padding(11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(43.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF292E37)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("♫", color = Orange, fontSize = 22.sp)
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                track.title,
                                color = White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(Modifier.height(3.dp))

                            Text(
                                track.artist,
                                color = Muted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (track.id in favorites) {
                            Text("♥", color = Orange)
                            Spacer(Modifier.width(7.dp))
                        }

                        Text(
                            formatTime(track.duration),
                            color = Muted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EqualizerScreen() {
    var enabled by remember { mutableStateOf(true) }

    val bands = remember {
        mutableStateListOf(0.5f, 0.5f, 0.5f, 0.5f, 0.5f)
    }

    val labels = listOf("60", "230", "910", "3.6k", "14k")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
    ) {
        Text(
            "Equalizador",
            color = White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 10.dp)
        )

        Text("Ajuste o perfil sonoro", color = Muted, fontSize = 12.sp)

        Spacer(Modifier.height(22.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(Panel)
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Equalizador", color = White)
                Text("Controle de bandas", color = Muted, fontSize = 11.sp)
            }

            Switch(
                checked = enabled,
                onCheckedChange = { enabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = Orange
                )
            )
        }

        Spacer(Modifier.height(25.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bands.forEachIndexed { index, value ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Slider(
                        value = value,
                        onValueChange = { bands[index] = it },
                        enabled = enabled,
                        valueRange = 0f..1f,
                        modifier = Modifier
                            .height(140.dp)
                            .width(40.dp)
                            .graphicsLayer { rotationZ = -90f },
                        colors = SliderDefaults.colors(
                            thumbColor = Orange,
                            activeTrackColor = Orange,
                            inactiveTrackColor = PanelLight
                        )
                    )

                    Text(labels[index], color = Muted, fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "PRESETS",
            color = Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Flat", "Bass", "Vocal", "Treble").forEach { preset ->
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val values = when (preset) {
                                "Bass" -> listOf(0.9f, 0.75f, 0.5f, 0.3f, 0.25f)
                                "Vocal" -> listOf(0.3f, 0.45f, 0.85f, 0.75f, 0.4f)
                                "Treble" -> listOf(0.25f, 0.35f, 0.5f, 0.8f, 0.95f)
                                else -> listOf(0.5f, 0.5f, 0.5f, 0.5f, 0.5f)
                            }

                            values.forEachIndexed { i, v -> bands[i] = v }
                        },
                    shape = RoundedCornerShape(10.dp),
                    color = Panel
                ) {
                    Text(
                        preset,
                        color = White,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            "Os ajustes são visuais nesta versão. A integração com o processamento de áudio será feita em uma próxima etapa.",
            color = Muted,
            fontSize = 11.sp
        )
    }
}

@Composable
fun MenuScreen(
    walkman: Boolean,
    privateMode: Boolean,
    onWalkman: (Boolean) -> Unit,
    onPrivate: (Boolean) -> Unit,
    onLibrary: () -> Unit,
    onHome: () -> Unit,
    onEqualizer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
    ) {
        Text(
            "Menu",
            color = White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 10.dp)
        )

        Text("Personalize sua experiência", color = Muted, fontSize = 12.sp)

        Spacer(Modifier.height(20.dp))

        MenuItem("⌂", "Início", "Voltar ao player", onHome)
        MenuItem("♫", "Biblioteca", "Suas músicas locais", onLibrary)
        MenuItem("≋", "Equalizador", "Configurações de áudio", onEqualizer)

        Spacer(Modifier.height(16.dp))

        SettingSwitch(
            "Modo Walkman",
            "Aparência inspirada em players clássicos",
            walkman,
            onWalkman
        )

        SettingSwitch(
            "Modo privado",
            "Opção visual de privacidade",
            privateMode,
            onPrivate
        )

        Spacer(Modifier.height(25.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(17.dp))
                .background(Panel)
                .padding(18.dp)
        ) {
            Column {
                Text(
                    "GIVARO",
                    color = Orange,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )

                Spacer(Modifier.height(5.dp))

                Text("Seu espaço para música.", color = Muted, fontSize = 12.sp)

                Spacer(Modifier.height(12.dp))

                Text("Versão 2.0", color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun MenuItem(
    symbol: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Panel)
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(symbol, color = Orange, fontSize = 24.sp)

        Spacer(Modifier.width(13.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 10.sp)
        }

        Text("›", color = Muted, fontSize = 24.sp)
    }
}

@Composable
fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 10.sp)
        }

        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Orange
            )
        )
    }
}

@Composable
fun BottomNavigation(
    selected: GivaroTab,
    onSelect: (GivaroTab) -> Unit
) {
    val items = listOf(
        Triple(GivaroTab.HOME, "⌂", "Início"),
        Triple(GivaroTab.LIBRARY, "♫", "Biblioteca"),
        Triple(GivaroTab.EQUALIZER, "≋", "EQ"),
        Triple(GivaroTab.MENU, "☰", "Menu")
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF101216))
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        items.forEach { (tab, symbol, label) ->
            val active = selected == tab

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(tab) }
                    .padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    symbol,
                    color = if (active) Orange else Muted,
                    fontSize = 21.sp
                )

                Text(
                    label,
                    color = if (active) Orange else Muted,
                    fontSize = 9.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun StreamingScreen(
    service: String,
    onClose: () -> Unit
) {
    val urls = mapOf(
        "Spotify" to "https://open.spotify.com/",
        "YouTube Music" to "https://music.youtube.com/",
        "Apple Music" to "https://music.apple.com/",
        "Deezer" to "https://www.deezer.com/",
        "Amazon Music" to "https://music.amazon.com/",
        "YouTube" to "https://www.youtube.com/"
    )

    Dialog(
        onDismissRequest = onClose
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .clip(RoundedCornerShape(18.dp))
                .background(Background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Panel)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClose) {
                    Text("‹ VOLTAR", color = Orange)
                }

                Text(
                    service,
                    color = White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.weight(1f))

                Text(
                    "GIVARO",
                    color = Orange,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.mixedContentMode =
                            android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW

                        webViewClient = object : WebViewClient() {
                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?
                            ) {
                                handler?.cancel()
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                return false
                            }
                        }

                        webChromeClient = WebChromeClient()

                        loadUrl(urls[service] ?: "https://www.youtube.com/")
                    }
                }
            )
        }
    }
}

fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L

    return String.format(
        Locale.getDefault(),
        "%d:%02d",
        minutes,
        seconds
    )
}
