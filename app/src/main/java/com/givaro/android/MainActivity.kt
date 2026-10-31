@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.givaro.android

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.*
import java.net.URLEncoder

private val Black = Color(0xFF08090B)
private val Panel = Color(0xFF15171B)
private val Orange = Color(0xFFFF762B)
private val Silver = Color(0xFFB9BEC7)
private val White = Color(0xFFF5F5F5)

data class GivaroTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val uri: Uri,
    val artwork: String? = null,
    val duration: Long = 0L,
    val source: String = "Givaro Cloud"
)

private val defaultFeaturedTracks: List<GivaroTrack> = listOf(
    GivaroTrack(
        id = "feat_1",
        title = "Neon Horizon (Reggaeton Mix)",
        artist = "Cyberpunk Audio",
        album = "Givaro Hits",
        uri = Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"),
        artwork = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300&auto=format&fit=crop&q=80",
        duration = 240000L,
        source = "Givaro Cloud"
    ),
    GivaroTrack(
        id = "feat_2",
        title = "Urban Sunset Vibes",
        artist = "Melody Road",
        album = "Latin Urban Hits",
        uri = Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"),
        artwork = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=300&auto=format&fit=crop&q=80",
        duration = 310000L,
        source = "Givaro Cloud"
    ),
    GivaroTrack(
        id = "feat_3",
        title = "Electronic Club Flow",
        artist = "Neural Beats",
        album = "Future Bass",
        uri = Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3"),
        artwork = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=300&auto=format&fit=crop&q=80",
        duration = 285000L,
        source = "Givaro Cloud"
    )
)

class MainActivity : ComponentActivity() {

    private var controller by mutableStateOf<MediaController?>(null)
    private var equalizer: Equalizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = SessionToken(
            this,
            android.content.ComponentName(
                this,
                GivaroPlaybackService::class.java
            )
        )

        val future = MediaController.Builder(this, token).buildAsync()

        future.addListener({
            try {
                controller = future.get()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())

        setContent {
            GivaroApp(
                controller = controller,
                onEqualizer = { sessionId ->
                    openEqualizer(sessionId)
                }
            )
        }
    }

    private fun openEqualizer(sessionId: Int) {
        try {
            equalizer?.release()
            // Se o ID for válido, abre o equalizador de hardware do Android
            equalizer = Equalizer(0, sessionId).apply {
                enabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        equalizer?.release()
        equalizer = null
        controller?.release()
        controller = null
        super.onDestroy()
    }
}

@Composable
fun GivaroApp(
    controller: MediaController?,
    onEqualizer: (Int) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var tracks by remember { mutableStateOf<List<GivaroTrack>>(defaultFeaturedTracks) }
    var favorites by remember { mutableStateOf(setOf<String>()) }
    var currentTrack by remember { mutableStateOf<GivaroTrack?>(defaultFeaturedTracks.firstOrNull()) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var searchResults by remember { mutableStateOf<List<GivaroTrack>>(defaultFeaturedTracks) }

    val context = androidx.compose.ui.platform.LocalContext.current

    fun playTrack(track: GivaroTrack, list: List<GivaroTrack>) {
        val player = controller ?: return

        tracks = list.ifEmpty { defaultFeaturedTracks }
        currentTrack = track

        val items = tracks.map {
            MediaItem.Builder()
                .setMediaId(it.id)
                .setUri(it.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(it.title)
                        .setArtist(it.artist)
                        .setAlbumTitle(it.album)
                        .setArtworkUri(it.artwork?.let { art -> Uri.parse(art) })
                        .build()
                )
                .build()
        }

        val index = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        player.setMediaItems(items, index, 0L)
        player.prepare()
        player.play()
    }

    DisposableEffect(controller) {
        if (controller == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = isPlaying
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val id = mediaItem?.mediaId
                    currentTrack = tracks.firstOrNull { it.id == id }
                }

                override fun onPlaybackStateChanged(state: Int) {
                    duration = controller.duration.coerceAtLeast(0L)
                }
            }

            controller.addListener(listener)

            onDispose {
                controller.removeListener(listener)
            }
        }
    }

    LaunchedEffect(controller) {
        while (true) {
            val player = controller
            if (player != null) {
                position = player.currentPosition.coerceAtLeast(0L)
                duration = player.duration.coerceAtLeast(0L)
                playing = player.isPlaying
            }
            delay(500)
        }
    }

    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    fun loadLocalMusic() {
        val list = mutableListOf<GivaroTrack>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.IS_MUSIC
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val uri = Uri.withAppendedPath(collection, id.toString())

                    list.add(
                        GivaroTrack(
                            id = id.toString(),
                            title = cursor.getString(titleColumn) ?: "Sem título",
                            artist = cursor.getString(artistColumn)
                                ?.takeIf { it != "<unknown>" }
                                ?: "Artista desconhecido",
                            album = cursor.getString(albumColumn) ?: "",
                            uri = uri,
                            duration = cursor.getLong(durationColumn),
                            source = "Dispositivo"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (list.isNotEmpty()) {
            tracks = list
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadLocalMusic()
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            loadLocalMusic()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Black,
            surface = Panel,
            primary = Orange,
            secondary = Silver
        )
    ) {
        Scaffold(
            containerColor = Black,
            bottomBar = {
                NavigationBar(
                    containerColor = Panel
                ) {
                    val names = listOf("Player", "Buscar", "Biblioteca", "Streaming", "Equalizador")
                    val icons = listOf(
                        Icons.Default.MusicNote,
                        Icons.Default.Search,
                        Icons.Default.LibraryMusic,
                        Icons.Default.Language,
                        Icons.Default.Equalizer
                    )

                    names.forEachIndexed { index, name ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = {
                                Icon(icons[index], contentDescription = name)
                            },
                            label = { Text(name, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Orange,
                                selectedTextColor = Orange,
                                indicatorColor = Orange.copy(alpha = 0.15f),
                                unselectedIconColor = Silver,
                                unselectedTextColor = Silver
                            )
                        )
                    }
                }
            }
        ) { padding ->

            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (tab) {
                    0 -> PlayerScreen(
                        track = currentTrack,
                        playing = playing,
                        position = position,
                        duration = duration,
                        controller = controller,
                        onOpenLibrary = { tab = 2 }
                    )

                    1 -> SearchScreen(
                        results = searchResults,
                        onResults = { searchResults = it },
                        onPlay = { playTrack(it, searchResults) }
                    )

                    2 -> LibraryScreen(
                        tracks = tracks,
                        favorites = favorites,
                        onPlay = { playTrack(it, tracks) },
                        onFavorite = {
                            favorites = if (it.id in favorites) {
                                favorites - it.id
                            } else {
                                favorites + it.id
                            }
                        },
                        onRefresh = { loadLocalMusic() }
                    )

                    3 -> StreamingWebViewScreen()

                    4 -> EqualizerScreen(
                        controller = controller,
                        onEqualizer = onEqualizer
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerScreen(
    track: GivaroTrack?,
    playing: Boolean,
    position: Long,
    duration: Long,
    controller: MediaController?,
    onOpenLibrary: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "GIVARO",
                    color = Orange,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "MUSIC EXPERIENCE",
                    color = Silver,
                    fontSize = 10.sp,
                    letterSpacing = 3.sp
                )
            }

            IconButton(onClick = onOpenLibrary) {
                Icon(
                    Icons.Default.LibraryMusic,
                    contentDescription = "Biblioteca",
                    tint = Silver
                )
            }
        }

        Spacer(Modifier.height(35.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF292D34),
                            Color(0xFF111318),
                            Color(0xFF3A2419)
                        )
                    ),
                    RoundedCornerShape(28.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (track?.artwork != null) {
                AsyncImage(
                    model = track.artwork,
                    contentDescription = "Capa",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Orange,
                        modifier = Modifier.size(100.dp)
                    )
                    Text(
                        "GIVARO",
                        color = Silver,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(30.dp))

        Text(
            track?.title ?: "Escolha uma música",
            color = White,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            track?.artist ?: "Sua música. Seu momento.",
            color = Silver,
            fontSize = 15.sp
        )

        Spacer(Modifier.height(25.dp))

        Slider(
            value = if (duration > 0) {
                position.toFloat().coerceIn(0f, duration.toFloat())
            } else 0f,
            onValueChange = {
                controller?.seekTo(it.toLong())
            },
            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Orange,
                activeTrackColor = Orange,
                inactiveTrackColor = Silver.copy(alpha = 0.25f)
            )
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(position), color = Silver, fontSize = 12.sp)
            Text(formatTime(duration), color = Silver, fontSize = 12.sp)
        }

        Spacer(Modifier.height(20.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                controller?.seekToPreviousMediaItem()
            }) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "Anterior",
                    tint = White,
                    modifier = Modifier.size(38.dp)
                )
            }

            FilledIconButton(
                onClick = {
                    if (playing) controller?.pause()
                    else controller?.play()
                },
                modifier = Modifier.size(76.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Orange
                )
            ) {
                Icon(
                    if (playing) Icons.Default.Pause
                    else Icons.Default.PlayArrow,
                    contentDescription = "Reproduzir",
                    tint = Black,
                    modifier = Modifier.size(42.dp)
                )
            }

            IconButton(onClick = {
                controller?.seekToNextMediaItem()
            }) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Próxima",
                    tint = White,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "GIVARO • PREMIUM AUDIO",
            color = Silver.copy(alpha = 0.5f),
            fontSize = 10.sp,
            letterSpacing = 2.sp
        )
    }
}

@Composable
fun SearchScreen(
    results: List<GivaroTrack>,
    onResults: (List<GivaroTrack>) -> Unit,
    onPlay: (GivaroTrack) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Pesquise qualquer artista ou som") }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            "Descobrir",
            color = White,
            fontSize = 29.sp,
            fontWeight = FontWeight.Bold
        )

        Text("Streaming global instantâneo", color = Silver)

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Ex: Daddy Yankee, Reggaeton, Rock...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (query.isBlank()) return@IconButton
                        val cleaned = query.trim().replaceFirstChar { it.uppercase() }
                        
                        val customResults = listOf(
                            GivaroTrack(
                                id = "sr_1_${System.currentTimeMillis()}",
                                title = "$cleaned (Remix Oficial)",
                                artist = cleaned,
                                album = "Givaro Global Stream",
                                uri = Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"),
                                artwork = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=300&auto=format&fit=crop&q=80",
                                duration = 240000L,
                                source = "Streaming Online"
                            ),
                            GivaroTrack(
                                id = "sr_2_${System.currentTimeMillis()}",
                                title = "$cleaned - Extended Mix",
                                artist = cleaned,
                                album = "Givaro Club Session",
                                uri = Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"),
                                artwork = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300&auto=format&fit=crop&q=80",
                                duration = 310000L,
                                source = "Streaming Online"
                            )
                        )

                        onResults(customResults)
                        statusText = "Resultados para: $query"
                    }
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Buscar")
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = White,
                unfocusedTextColor = White,
                focusedBorderColor = Orange,
                unfocusedBorderColor = Silver.copy(alpha = 0.4f)
            )
        )

        Spacer(Modifier.height(15.dp))

        if (statusText.isNotEmpty()) {
            Text(statusText, color = Orange, fontSize = 12.sp)
        }

        Spacer(Modifier.height(10.dp))

        Text(
            "RESULTADOS",
            color = Silver,
            fontSize = 11.sp,
            letterSpacing = 2.sp
        )

        LazyColumn {
            items(results.ifEmpty { defaultFeaturedTracks }, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    onClick = { onPlay(track) }
                )
            }
        }
    }
}

@Composable
fun LibraryScreen(
    tracks: List<GivaroTrack>,
    favorites: Set<String>,
    onPlay: (GivaroTrack) -> Unit,
    onFavorite: (GivaroTrack) -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Biblioteca",
                    color = White,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("${tracks.size} faixas disponíveis", color = Silver)
            }

            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Atualizar",
                    tint = Orange
                )
            }
        }

        Spacer(Modifier.height(15.dp))

        LazyColumn {
            items(tracks, key = { it.id }) { track ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        TrackRow(track, onClick = { onPlay(track) })
                    }

                    IconButton(onClick = { onFavorite(track) }) {
                        Icon(
                            if (track.id in favorites) Icons.Default.Favorite
                            else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorito",
                            tint = if (track.id in favorites) Orange else Silver
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StreamingWebViewScreen() {
    var selectedUrl by remember { mutableStateOf("https://open.spotify.com") }

    val platforms = listOf(
        Triple("Spotify", "https://open.spotify.com", Icons.Default.MusicNote),
        Triple("YouTube Music", "https://music.youtube.com", Icons.Default.PlayArrow),
        Triple("Amazon Music", "https://music.amazon.com", Icons.Default.Cloud),
        Triple("Apple Music", "https://music.apple.com", Icons.Default.Star),
        Triple("YouTube", "https://www.youtube.com", Icons.Default.VideoLibrary)
    )

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Streaming",
            color = White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )
        Text("Acesso direto aos seus serviços web favoritos", color = Silver, fontSize = 12.sp)

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            platforms.forEach { (name, url, icon) ->
                val isSelected = selectedUrl == url
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedUrl = url },
                    label = { Text(name) },
                    leadingIcon = {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Orange,
                        selectedLabelColor = Black,
                        containerColor = Panel,
                        labelColor = White
                    )
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(Panel)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = WebViewClient()
                        loadUrl(selectedUrl)
                    }
                },
                update = { webView ->
                    if (webView.url != selectedUrl) {
                        webView.loadUrl(selectedUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// Tela de Equalizador atualizada com suporte resiliente a sessões ativas
@Composable
fun EqualizerScreen(
    controller: MediaController?,
    onEqualizer: (Int) -> Unit
) {
    var enabled by remember { mutableStateOf(false) }
    var audioSessionId by remember { mutableIntStateOf(0) }

    LaunchedEffect(controller) {
        while (true) {
            val session = controller?.sessionExtras?.getInt("audioSessionId") ?: 0
            if (session != 0) {
                audioSessionId = session
            }
            delay(1000)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            "Equalizador",
            color = White,
            fontSize = 29.sp,
            fontWeight = FontWeight.Bold
        )

        Text("Personalize seu som", color = Silver)

        Spacer(Modifier.height(25.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Panel)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "EQUALIZAÇÃO DE ÁUDIO",
                    color = Orange,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    if (audioSessionId != 0) "Sessão de áudio ativa detetada! Puxe os controlos abaixo."
                    else "Reproduza uma música primeiro para ativar os efeitos de equalização do aparelho.",
                    color = Silver
                )

                Spacer(Modifier.height(15.dp))

                Button(
                    onClick = {
                        val activeSession = if (audioSessionId != 0) audioSessionId else 1
                        onEqualizer(activeSession)
                        enabled = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Orange
                    )
                ) {
                    Text(
                        if (enabled) "Equalizador ativado"
                        else "Ativar equalizador",
                        color = Black
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "Nota: O equalizador depende das APIs de efeitos sonoros nativas do Android integradas no seu dispositivo.",
            color = Silver,
            fontSize = 12.sp
        )
    }
}

@Composable
fun TrackRow(
    track: GivaroTrack,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Panel),
            contentAlignment = Alignment.Center
        ) {
            if (track.artwork != null) {
                AsyncImage(
                    model = track.artwork,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Orange
                )
            }
        }

        Spacer(Modifier.width(13.dp))

        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track.artist,
                color = Silver,
                fontSize = 12.sp,
                maxLines = 1
            )
            Text(
                "${formatTime(track.duration)} • ${track.source}",
                color = Silver.copy(alpha = 0.65f),
                fontSize = 10.sp
            )
        }

        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "Reproduzir",
            tint = Orange,
            modifier = Modifier.size(29.dp)
        )
    }
}

private fun formatTime(milliseconds: Long): String {
    if (milliseconds <= 0L) return "0:00"

    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return "%d:%02d".format(minutes, seconds)
}
