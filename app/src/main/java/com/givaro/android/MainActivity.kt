package com.givaro.android

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.SslErrorHandler
import android.net.http.SslError
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.random.Random

private val Background = Color(0xFF08090C)
private val Panel = Color(0xFF15181E)
private val PanelLight = Color(0xFF20242C)
private val Orange = Color(0xFFFF9418)
private val OrangeLight = Color(0xFFFFB34D)
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
            if (it) {
                setContent {
                    GivaroApp()
                }
            } else {
                setContent {
                    GivaroApp()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(permission)
        } else {
            setContent {
                GivaroApp()
            }
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
    val context = androidx.compose.ui.platform.LocalContext.current

    val tracks = remember { mutableStateListOf<MusicTrack>() }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var tab by remember { mutableStateOf(GivaroTab.HOME) }
    var playing by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    var volume by remember { mutableFloatStateOf(0.8f) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(false) }
    var favoriteIds by remember { mutableStateOf(setOf<Long>()) }
    var searchText by remember { mutableStateOf("") }
    var selectedService by remember { mutableStateOf<String?>(null) }
    var walkmanMode by remember { mutableStateOf(false) }
    var privateMode by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    val currentTrack = tracks.getOrNull(selectedIndex)

    fun loadTracks() {
        tracks.clear()

        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Faixa desconhecida"
                    val artist = cursor.getString(artistColumn) ?: "Artista desconhecido"
                    val album = cursor.getString(albumColumn) ?: "Álbum desconhecido"
                    val length = cursor.getLong(durationColumn)

                    val uri = Uri.withAppendedPath(
                        collection,
                        id.toString()
                    )

                    tracks.add(
                        MusicTrack(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            uri = uri,
                            duration = length
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }

        if (selectedIndex >= tracks.size) {
            selectedIndex = 0
        }
    }

    LaunchedEffect(Unit) {
        loadTracks()
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

    fun playTrack(index: Int) {
        if (tracks.isEmpty()) return

        val safeIndex = index.coerceIn(0, tracks.lastIndex)
        selectedIndex = safeIndex
        currentPosition = 0L
        prepared = false

        try {
            player?.release()
            player = null

            val newPlayer = MediaPlayer()
            player = newPlayer

            newPlayer.setDataSource(context, tracks[safeIndex].uri)

            newPlayer.setOnPreparedListener { mp ->
                prepared = true
                duration = mp.duration.toLong().coerceAtLeast(1L)
                mp.setVolume(volume, volume)
                if (playing) {
                    mp.start()
                }
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

        if (currentPosition > 3000L) {
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

    LaunchedEffect(playing, prepared) {
        if (prepared) {
            try {
                if (playing) player?.start() else player?.pause()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(playing, prepared, selectedIndex) {
        while (playing && prepared) {
            try {
                currentPosition = player?.currentPosition?.toLong() ?: 0L
                duration = player?.duration?.toLong()?.coerceAtLeast(1L) ?: 1L
            } catch (_: Exception) {
            }
            kotlinx.coroutines.delay(500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .systemBarsPadding()
    ) {
        GivaroHeader(
            onEqualizer = { tab = GivaroTab.EQUALIZER },
            onMenu = { tab = GivaroTab.MENU }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (tab) {
                GivaroTab.HOME -> {
                    HomeScreen(
                        track = currentTrack,
                        playing = playing,
                        prepared = prepared,
                        position = currentPosition,
                        duration = duration,
                        volume = volume,
                        shuffle = shuffle,
                        repeat = repeat,
                        favorite = currentTrack?.id in favoriteIds,
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
                            currentPosition = it
                            if (prepared) {
                                try {
                                    player?.seekTo(it.toInt())
                                } catch (_: Exception) {
                                }
                            }
                        },
                        onFavorite = {
                            currentTrack?.let { track ->
                                favoriteIds = if (track.id in favoriteIds) {
                                    favoriteIds - track.id
                                } else {
                                    favoriteIds + track.id
                                }
                            }
                        },
                        onOpenLibrary = { tab = GivaroTab.LIBRARY },
                        onService = { selectedService = it }
                    )
                }

                GivaroTab.LIBRARY -> {
                    LibraryScreen(
                        tracks = tracks,
                        selectedIndex = selectedIndex,
                        favorites = favoriteIds,
                        searchText = searchText,
                        onSearch = { searchText = it },
                        onRefresh = { loadTracks() },
                        onSelect = {
                            playing = true
                            playTrack(it)
                            tab = GivaroTab.HOME
                        }
                    )
                }

                GivaroTab.EQUALIZER -> {
                    EqualizerScreen()
                }

                GivaroTab.MENU -> {
                    MenuScreen(
                        walkmanMode = walkmanMode,
                        privateMode = privateMode,
                        onWalkman = { walkmanMode = it },
                        onPrivate = { privateMode = it },
                        onLibrary = { tab = GivaroTab.LIBRARY },
                        onHome = { tab = GivaroTab.HOME },
                        onEqualizer = { tab = GivaroTab.EQUALIZER }
                    )
                }
            }
        }

        BottomNavigation(
            selected = tab,
            onSelect = { tab = it }
        )
    }

    selectedService?.let { service ->
        StreamingScreen(
            service = service,
            onClose = { selectedService = null }
        )
    }
}

@Composable
fun GivaroHeader(
    onEqualizer: () -> Unit,
    onMenu: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
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

        IconButton(onClick = onEqualizer) {
            Icon(
                Icons.Default.Equalizer,
                contentDescription = "Equalizador",
                tint = Muted
            )
        }

        IconButton(onClick = onMenu) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Menu",
                tint = White
            )
        }
    }
}

@Composable
fun HomeScreen(
    track: MusicTrack?,
    playing: Boolean,
    prepared: Boolean,
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
    onOpenLibrary: () -> Unit,
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
                .padding(top = 4.dp, bottom = 15.dp),
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

            TextButton(onClick = onOpenLibrary) {
                Text("BIBLIOTECA", color = Orange, fontSize = 10.sp)
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Orange,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        AlbumArtwork(
            track = track,
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(1f)
        )

        Spacer(Modifier.height(23.dp))

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

            IconButton(onClick = onFavorite) {
                Icon(
                    if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favoritar",
                    tint = if (favorite) Orange else Muted
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Slider(
            value = position.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = Orange,
                activeTrackColor = Orange,
                inactiveTrackColor = PanelLight
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(position), color = Muted, fontSize = 10.sp)
            Text(formatTime(duration), color = Muted, fontSize = 10.sp)
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onShuffle) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = "Aleatório",
                    tint = if (shuffle) Orange else Muted
                )
            }

            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "Anterior",
                    tint = White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Orange),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Reproduzir",
                        tint = Color.Black,
                        modifier = Modifier.size(37.dp)
                    )
                }
            }

            IconButton(
                onClick = onNext,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Próxima",
                    tint = White,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(onClick = onRepeat) {
                Icon(
                    Icons.Default.Repeat,
                    contentDescription = "Repetir",
                    tint = if (repeat) Orange else Muted
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.VolumeDown,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(18.dp)
            )
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
            Icon(
                Icons.Default.VolumeUp,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.height(23.dp))

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

        Spacer(Modifier.height(12.dp))

        services.chunked(3).forEach { rowServices ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                rowServices.forEach { service ->
                    ServiceTile(
                        service = service,
                        modifier = Modifier.weight(1f),
                        onClick = { onService(service) }
                    )
                }
                repeat(3 - rowServices.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(9.dp))
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun AlbumArtwork(
    track: MusicTrack?,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current

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
                        Color(0xFF282D36),
                        Color(0xFF11141A),
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
                Text(
                    "G",
                    color = Orange,
                    fontSize = 112.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp)
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.GraphicEq,
                contentDescription = null,
                tint = Orange,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ServiceTile(
    service: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val symbol = when (service) {
        "Spotify" -> "S"
        "YouTube Music" -> "▶"
        "Apple Music" -> "♫"
        "Deezer" -> "D"
        "Amazon Music" -> "a"
        else -> "▶"
    }

    Column(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Panel)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(PanelLight),
            contentAlignment = Alignment.Center
        ) {
            Text(
                symbol,
                color = Orange,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(5.dp))

        Text(
            service,
            color = White,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun LibraryScreen(
    tracks: List<MusicTrack>,
    selectedIndex: Int,
    favorites: Set<Long>,
    searchText: String,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val filtered = tracks.withIndex().filter { (_, track) ->
        track.title.contains(searchText, true) ||
                track.artist.contains(searchText, true) ||
                track.album.contains(searchText, true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 17.dp),
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

            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Atualizar",
                    tint = Orange
                )
            }
        }

        OutlinedTextField(
            value = searchText,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("Buscar música ou artista", color = Muted, fontSize = 13.sp)
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = Muted)
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

        Spacer(Modifier.height(15.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 45.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Muted,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (tracks.isEmpty()) "Nenhuma música encontrada" else "Nenhum resultado",
                        color = White,
                        fontSize = 15.sp
                    )
                    Text(
                        "Adicione músicas ao dispositivo e atualize a biblioteca.",
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(bottom = 18.dp)
            ) {
                itemsIndexed(filtered) { _, item ->
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
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Orange
                            )
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
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = "Favorita",
                                tint = Orange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
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
        mutableStateListOf(0.2f, 0.4f, 0.1f, 0.5f, 0.3f)
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

        Text(
            "Ajuste o perfil sonoro",
            color = Muted,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(Panel)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Equalizador", color = White, fontWeight = FontWeight.SemiBold)
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

        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bands.forEachIndexed { index, value ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Slider(
                        value = value,
                        onValueChange = { bands[index] = it },
                        enabled = enabled,
                        valueRange = 0f..1f,
                        modifier = Modifier
                            .height(165.dp)
                            .width(42.dp)
                            .graphicsLayer { rotationZ = -90f },
                        colors = SliderDefaults.colors(
                            thumbColor = Orange,
                            activeTrackColor = Orange,
                            inactiveTrackColor = PanelLight,
                            disabledThumbColor = Muted
                        )
                    )
                    Text(labels[index], color = Muted, fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        Text(
            "PRESETS",
            color = Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            "Os controles desta tela são visuais nesta versão. A aplicação real dos efeitos de áudio será integrada em uma próxima etapa.",
            color = Muted,
            fontSize = 11.sp
        )
    }
}

@Composable
fun MenuScreen(
    walkmanMode: Boolean,
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

        Text(
            "Personalize sua experiência",
            color = Muted,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(22.dp))

        MenuAction(
            icon = Icons.Default.Home,
            title = "Início",
            subtitle = "Voltar ao player",
            onClick = onHome
        )

        MenuAction(
            icon = Icons.Default.LibraryMusic,
            title = "Biblioteca",
            subtitle = "Suas músicas locais",
            onClick = onLibrary
        )

        MenuAction(
            icon = Icons.Default.Equalizer,
            title = "Equalizador",
            subtitle = "Configurações de áudio",
            onClick = onEqualizer
        )

        Spacer(Modifier.height(18.dp))

        SettingSwitch(
            title = "Modo Walkman",
            subtitle = "Aparência inspirada em players clássicos",
            checked = walkmanMode,
            onChecked = onWalkman
        )

        SettingSwitch(
            title = "Modo privado",
            subtitle = "Opção visual de privacidade",
            checked = privateMode,
            onChecked = onPrivate
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
                Text(
                    "Seu espaço para música.",
                    color = Muted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Versão 2.0",
                    color = Muted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun MenuAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
        Icon(icon, contentDescription = null, tint = Orange)
        Spacer(Modifier.width(13.dp))
        Column {
            Text(title, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 10.sp)
        }
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Muted
        )
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
        Triple(GivaroTab.HOME, Icons.Default.Home, "Início"),
        Triple(GivaroTab.LIBRARY, Icons.Default.LibraryMusic, "Biblioteca"),
        Triple(GivaroTab.EQUALIZER, Icons.Default.Equalizer, "EQ"),
        Triple(GivaroTab.MENU, Icons.Default.Menu, "Menu")
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF101216))
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (tab, icon, label) ->
            val active = selected == tab

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(tab) }
                    .padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (active) Orange else Muted,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.height(3.dp))
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

    DialogLikeOverlay {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
                .systemBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Panel)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Voltar",
                        tint = White
                    )
                }

                Text(
                    service,
                    color = White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.weight(1f))

                Text(
                    "GIVARO",
                    color = Orange,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
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
                },
                update = {}
            )
        }
    }
}

@Composable
fun DialogLikeOverlay(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        content()
    }
}

fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
