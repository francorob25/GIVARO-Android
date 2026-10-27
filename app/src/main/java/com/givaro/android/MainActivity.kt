package com.givaro.android

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import java.util.Locale

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

private data class Service(val name: String, val url: String, val screenKey: String)

private val services = listOf(
    Service("Apple", "https://music.apple.com", "apple"),
    Service("Spotify", "https://open.spotify.com", "spotify"),
    Service("Deezer", "https://www.deezer.com", "deezer"),
    Service("Amazon", "https://music.amazon.com", "amazon"),
    Service("YouTube", "https://music.youtube.com", "youtube")
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
                if (granted) "Permissão autorizada. Atualize as músicas."
                else "Permissão de áudio necessária.",
                Toast.LENGTH_LONG
            ).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

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
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var loadKey by remember { mutableIntStateOf(0) }
    var isFavorite by remember { mutableStateOf(false) }

    val track = tracks.getOrNull(selected)

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

                val idCol = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media._ID
                )
                val titleCol = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.TITLE
                )
                val artistCol = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.ARTIST
                )
                val durationCol = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.DURATION
                )

                while (cursor.moveToNext()) {
                    val title =
                        cursor.getString(titleCol) ?: "Faixa sem título"

                    if (
                        title.contains("WhatsApp", true) ||
                        title.contains("Voice Note", true)
                    ) continue

                    val id = cursor.getLong(idCol)

                    tracks.add(
                        Track(
                            id = id,
                            title = title,
                            artist = cursor.getString(artistCol)
                                ?.takeIf {
                                    it.isNotBlank() && it != "<unknown>"
                                }
                                ?: "Artista desconhecido",
                            duration = cursor.getLong(durationCol),
                            uri = ContentUris.withAppendedId(
                                collection,
                                id
                            )
                        )
                    )
                }
            }

            selected = selected.coerceIn(
                0,
                (tracks.size - 1).coerceAtLeast(0)
            )

            loadKey++

            Toast.makeText(
                context,
                "${tracks.size} músicas encontradas",
                Toast.LENGTH_SHORT
            ).show()

        } catch (_: Exception) {
            Toast.makeText(
                context,
                "Erro ao carregar músicas.",
                Toast.LENGTH_SHORT
            ).show()
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

                if (playing) {
                    try {
                        ready.start()
                    } catch (_: Exception) {
                    }
                }
            }

            mp.setOnCompletionListener {
                if (repeat) {
                    try {
                        it.seekTo(0)
                        it.start()
                    } catch (_: Exception) {
                    }
                } else if (tracks.isNotEmpty()) {
                    selected = if (shuffle && tracks.size > 1) {
                        tracks.indices.filter { it != selected }.random()
                    } else {
                        (selected + 1) % tracks.size
                    }

                    playing = true
                }
            }

            mp.prepareAsync()

        } catch (_: Exception) {
            playing = false

            Toast.makeText(
                context,
                "Não foi possível reproduzir esta música.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(playing) {
        try {
            val mp = player

            if (mp != null) {
                if (playing && !mp.isPlaying) {
                    mp.start()
                } else if (!playing && mp.isPlaying) {
                    mp.pause()
                }
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
                selected =
                    if (selected > 0) selected - 1
                    else tracks.lastIndex

                playing = true
            }
        } catch (_: Exception) {
            selected =
                if (selected > 0) selected - 1
                else tracks.lastIndex

            playing = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF20170D),
                        Black,
                        Black
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {

        // Cabeçalho
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { screen = "equalizer" }) {
                Text("EQ", color = Amber, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "GIVARO",
                    color = White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                )

                Text(
                    "HUB STREAMING",
                    color = Amber,
                    fontSize = 9.sp,
                    letterSpacing = 2.sp
                )
            }

            TextButton(onClick = { screen = "menu" }) {
                Text("☰", color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(5.dp))

        // Chips de Serviços de Streaming e Local
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ServiceChip("Local", screen == "player" || screen == "library") {
                screen = "player"
            }

            services.forEach { service ->
                ServiceChip(service.name, screen == service.screenKey, true) {
                    screen = service.screenKey
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        when (screen) {

            // PLAYER LOCAL
            "player" -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF45321D),
                                    Panel,
                                    Color(0xFF24201A)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "EQ",
                            color = Amber,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            "GIVARO",
                            color = White,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp
                        )

                        Text(
                            "PREMIUM AUDIO",
                            color = Amber,
                            fontSize = 9.sp,
                            letterSpacing = 2.sp
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    track?.title ?: "Nenhuma música selecionada",
                    color = White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    track?.artist ?: "Escolha uma música da biblioteca",
                    color = Muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Slider(
                    value = if (duration > 0) {
                        position.coerceIn(0f, duration.toFloat())
                    } else {
                        0f
                    },
                    onValueChange = { position = it },
                    onValueChangeFinished = {
                        try {
                            player?.seekTo(position.toInt())
                        } catch (_: Exception) {
                        }
                    },
                    valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                    enabled = track != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        timeText(position.toLong()),
                        color = Muted,
                        fontSize = 10.sp
                    )

                    Text(
                        timeText(duration.toLong()),
                        color = Muted,
                        fontSize = 10.sp
                    )
                }

                Spacer(Modifier.height(3.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { shuffle = !shuffle }) {
                        Text("⤮", color = if (shuffle) Amber else Muted, fontSize = 22.sp)
                    }

                    TextButton(onClick = { previous() }) {
                        Text("◀◀", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Amber)
                            .clickable {
                                if (tracks.isEmpty()) {
                                    if (hasPermission()) refreshTracks()
                                    else requestPermission()
                                } else {
                                    playing = !playing
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (playing) "⏸" else "▶",
                            color = Black,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TextButton(onClick = { next() }) {
                        Text("▶▶", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(onClick = { repeat = !repeat }) {
                        Text(if (repeat) "↻" else "🔁", color = if (repeat) Amber else Muted, fontSize = 20.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("VOL.", color = Muted, fontSize = 10.sp)

                    Slider(
                        value = volume,
                        onValueChange = { volume = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                    )

                    TextButton(onClick = { isFavorite = !isFavorite }) {
                        Text(
                            if (isFavorite) "♥" else "♡",
                            color = Amber,
                            fontSize = 20.sp
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = { screen = "library" },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Panel,
                        contentColor = Amber
                    )
                ) {
                    Text(
                        if (tracks.isEmpty()) "CARREGAR MINHAS MÚSICAS"
                        else "ABRIR BIBLIOTECA"
                    )
                }
            }

            // BIBLIOTECA
            "library" -> Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { screen = "player" }) {
                        Text("‹", color = Amber, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        "MINHA BIBLIOTECA",
                        color = White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.weight(1f))

                    Text(
                        "${tracks.size} faixas",
                        color = Muted,
                        fontSize = 11.sp
                    )
                }

                Button(
                    onClick = { refreshTracks() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Amber,
                        contentColor = Black
                    )
                ) {
                    Text("ATUALIZAR MÚSICAS")
                }

                if (tracks.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Nenhuma música encontrada.\nToque em ATUALIZAR MÚSICAS.",
                            color = Muted,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        itemsIndexed(tracks) { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (index == selected) {
                                            Color(0xFF302314)
                                        } else {
                                            Panel
                                        }
                                    )
                                    .clickable {
                                        selected = index
                                        playing = true
                                        screen = "player"
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("♫", color = Amber, fontSize = 20.sp)

                                Spacer(Modifier.width(10.dp))

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.title,
                                        color = White,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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

            // EQUALIZADOR
            "equalizer" -> Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { screen = "player" }) {
                        Text("‹", color = Amber, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        "EQUALIZADOR",
                        color = White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    "EQ",
                    color = Amber,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Text(
                    "Equalizador GIVARO",
                    color = White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(12.dp)
                )

                Text(
                    "O equalizador avançado depende do suporte de áudio do dispositivo. Use o controle de volume no player para ajustar o nível.",
                    color = Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // MENU
            "menu" -> Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { screen = "player" }) {
                        Text("‹", color = Amber, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        "MENU GIVARO",
                        color = White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(8.dp))

                MenuRow("Reprodutor", "Voltar ao player") {
                    screen = "player"
                }

                MenuRow("Biblioteca", "Músicas do dispositivo") {
                    screen = "library"
                }

                MenuRow("Equalizador", "Opções de áudio") {
                    screen = "equalizer"
                }

                services.forEach { service ->
                    MenuRow(
                        service.name,
                        "Abrir player web integrado"
                    ) {
                        screen = service.screenKey
                    }
                }
            }

            // STREAMING WEBVIEWS INTEGRADOS (Layout Imersivo sem margens pesadas)
            else -> {
                val currentService = services.find { it.screenKey == screen }
                if (currentService != null) {
                    StreamingWebViewScreen(
                        url = currentService.url,
                        onBack = { screen = "player" }
                    )
                }
            }
        }
    }
}

@Composable
fun StreamingWebViewScreen(url: String, onBack: () -> Unit) {
    var isLoaded by remember(url) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(Black),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Barra de topo interna limpa e integrada
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Panel)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("‹ Voltar", color = Amber, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "MODO HUB",
                color = Muted,
                fontSize = 10.sp,
                letterSpacing = 2.sp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            loadsImagesAutomatically = true
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(true)
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            cacheMode = WebSettings.LOAD_DEFAULT
                            mediaPlaybackRequiresUserGesture = false
                            
                            // User-Agent de telemóvel moderno
                            userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                        }

                        CookieManager.getInstance().setAcceptCookie(true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                isLoaded = false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoaded = true
                            }

                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                                handler?.proceed()
                            }
                        }

                        loadUrl(url)
                    }
                },
                update = { webView ->
                    if (webView.tag != url) {
                        webView.tag = url
                        webView.loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (!isLoaded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Black),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Amber)
                }
            }
        }
    }
}

@Composable
private fun ServiceChip(
    label: String,
    selected: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Amber else Panel)
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (compact) 7.dp else 11.dp,
                vertical = 9.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Black else White,
            fontSize = if (compact) 9.sp else 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun MenuRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Panel)
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = White,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                subtitle,
                color = Muted,
                fontSize = 12.sp
            )
        }

        Text("›", color = Amber, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

private fun timeText(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)

    String.format(
        Locale.getDefault(),
        "%d:%02d",
        totalSeconds / 60,
        totalSeconds % 60
    ).let { return it }
}
