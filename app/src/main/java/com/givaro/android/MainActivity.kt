
@file:OptIn(ExperimentalMaterial3Api::class)

package com.givaro.android

import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Station(
    val name: String,
    val streamUrl: String,
    val favicon: String,
    val genre: String,
    val source: String = "Rádio"
)

class MainActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GivaroTheme {
                GivaroApp(
                    onPlayStream = { url -> playAudio(url) },
                    onPauseStream = { pauseAudio() },
                    onResumeStream = { resumeAudio() }
                )
            }
        }
    }

    private fun playAudio(url: String) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setOnPreparedListener { start() }
                setOnErrorListener { _, _, _ ->
                    release()
                    mediaPlayer = null
                    true
                }
                setDataSource(url)
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun pauseAudio() {
        mediaPlayer?.let {
            if (it.isPlaying) it.pause()
        }
    }

    private fun resumeAudio() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                try {
                    it.start()
                } catch (e: IllegalStateException) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

@Composable
fun GivaroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            primary = Color(0xFFFFB74D),
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

@Composable
fun GivaroApp(
    onPlayStream: (String) -> Unit,
    onPauseStream: () -> Unit,
    onResumeStream: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var currentStation by remember { mutableStateOf<Station?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "GIVARO",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp,
                            color = Color(0xFFFFB74D)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212)
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF181818)) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, "Início") },
                    label = { Text("Início") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Search, "Buscar") },
                    label = { Text("Buscar") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.PlayArrow, "Biblioteca") },
                    label = { Text("Biblioteca") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, "EQ") },
                    label = { Text("EQ") }
                )
            }
        },
        containerColor = Color(0xFF121212)
    ) { innerPadding ->

        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0, 2, 3 -> MainPlayerScreen(
                    currentStation = currentStation,
                    isPlaying = isPlaying,
                    onPlayToggle = {
                        if (isPlaying) {
                            onPauseStream()
                            isPlaying = false
                        } else if (currentStation != null) {
                            onResumeStream()
                            isPlaying = true
                        }
                    }
                )

                1 -> StreamingSearchScreen(
                    onStationSelected = { station ->
                        currentStation = station
                        isPlaying = true
                        onPlayStream(station.streamUrl)
                        selectedTab = 0
                    }
                )
            }
        }
    }
}

@Composable
fun MainPlayerScreen(
    currentStation: Station?,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF10101C)),
            contentAlignment = Alignment.Center
        ) {
            if (!currentStation?.favicon.isNullOrBlank()) {
                AsyncImage(
                    model = currentStation?.favicon,
                    contentDescription = "Capa da música",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                NoCoverVisualizer(isPlaying = isPlaying)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                currentStation?.name ?: "GIVARO",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                currentStation?.let { "${it.source} • ${it.genre}" }
                    ?: "Seu universo musical",
                fontSize = 13.sp,
                color = Color.Gray,
                maxLines = 1
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF333333))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.3f)
                        .fillMaxHeight()
                        .background(Color(0xFFFFB74D))
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("GIVARO PLAYER", fontSize = 12.sp, color = Color.Gray)
                Text(
                    if (isPlaying) "TOCANDO" else "PRONTO",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {}) {
                Icon(
                    Icons.Default.ArrowBack,
                    "Anterior",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(
                onClick = onPlayToggle,
                modifier = Modifier
                    .size(68.dp)
                    .background(Color(0xFFFFB74D), RoundedCornerShape(50))
            ) {
                if (isPlaying) {
                    Text(
                        "Ⅱ",
                        color = Color.Black,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        Icons.Default.PlayArrow,
                        "Reproduzir",
                        tint = Color.Black,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            IconButton(onClick = {}) {
                Icon(
                    Icons.Default.ArrowForward,
                    "Próxima",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
fun NoCoverVisualizer(isPlaying: Boolean) {
    val colors = listOf(
        Color(0xFFFFB74D),
        Color(0xFFFF8A65),
        Color(0xFFE85DFF),
        Color(0xFF9C6BFF),
        Color(0xFF42C9FF)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF17132D),
                        Color(0xFF080B19),
                        Color(0xFF19102B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "GIVARO",
                fontSize = 25.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 7.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "AUDIO VISUALIZER",
                fontSize = 9.sp,
                letterSpacing = 4.sp,
                color = Color(0xFFFFB74D)
            )
            Spacer(modifier = Modifier.height(45.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(35) { index ->
                    val transition = rememberInfiniteTransition(
                        label = "visualizer_$index"
                    )
                    val animatedHeight by transition.animateFloat(
                        initialValue = 0.12f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 350 + (index % 5) * 120,
                                delayMillis = index * 35,
                                easing = FastOutSlowInEasing
                            ),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "bar_$index"
                    )
                    val barHeight = if (isPlaying) animatedHeight else 0.08f

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(barHeight.coerceIn(0.06f, 1f))
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        colors[index % colors.size],
                                        colors[(index + 2) % colors.size]
                                    )
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            Text(
                if (isPlaying) "NOW PLAYING" else "GIVARO PLAYER",
                fontSize = 10.sp,
                letterSpacing = 3.sp,
                color = Color.LightGray
            )
        }
    }
}

private fun httpGet(urlString: String): String {
    val connection = URL(urlString).openConnection() as HttpURLConnection
    connection.connectTimeout = 12000
    connection.readTimeout = 15000
    connection.setRequestProperty("User-Agent", "GIVARO/1.0")
    return try {
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

private fun encodeQuery(value: String): String =
    URLEncoder.encode(value, "UTF-8")

private fun searchAudius(query: String): List<Station> {
    val encoded = encodeQuery(query)
    val url =
        "https://discoveryprovider.audius.co/v1/tracks/search" +
        "?query=$encoded&app_name=GIVARO"

    val root = JSONObject(httpGet(url))
    val data = root.optJSONArray("data") ?: JSONArray()
    val results = mutableListOf<Station>()

    for (i in 0 until minOf(data.length(), 15)) {
        val item = data.optJSONObject(i) ?: continue
        val id = item.optString("id", "")
        if (id.isBlank()) continue

        val title = item.optString("title", "Faixa sem título")
        val user = item.optJSONObject("user")
        val artist = user?.optString("name", "Artista desconhecido")
            ?: "Artista desconhecido"

        val artwork = item.optJSONObject("artwork")
        val cover = artwork?.optString("480x480", "")
            ?: ""

        val stream =
            "https://discoveryprovider.audius.co/v1/tracks/$id/stream?app_name=GIVARO"

        results.add(
            Station(
                name = "$title — $artist",
                streamUrl = stream,
                favicon = cover,
                genre = item.optString("genre", "Audius"),
                source = "Audius"
            )
        )
    }
    return results
}

private fun searchInternetArchive(query: String): List<Station> {
    val encoded = encodeQuery(query)
    val searchUrl =
        "https://archive.org/advancedsearch.php" +
        "?q=$encoded%20AND%20mediatype%3Aaudio" +
        "&fl[]=identifier&fl[]=title&fl[]=creator" +
        "&rows=12&page=1&output=json"

    val root = JSONObject(httpGet(searchUrl))
    val response = root.optJSONObject("response")
    val docs = response?.optJSONArray("docs") ?: JSONArray()
    val results = mutableListOf<Station>()

    for (i in 0 until minOf(docs.length(), 10)) {
        val doc = docs.optJSONObject(i) ?: continue
        val identifier = doc.optString("identifier", "")
        if (identifier.isBlank()) continue

        try {
            val metadataUrl =
                "https://archive.org/metadata/$identifier"
            val metadata = JSONObject(httpGet(metadataUrl))
            val files = metadata.optJSONArray("files") ?: JSONArray()

            var audioFile = ""

            for (j in 0 until files.length()) {
                val file = files.optJSONObject(j) ?: continue
                val name = file.optString("name", "")
                val format = file.optString("format", "").lowercase()

                val isAudio =
                    name.endsWith(".mp3", true) ||
                    name.endsWith(".ogg", true) ||
                    name.endsWith(".m4a", true) ||
                    name.endsWith(".flac", true) ||
                    name.endsWith(".wav", true) ||
                    name.endsWith(".aac", true)

                val isMetadata =
                    name.contains("_meta") ||
                    name.contains("_files") ||
                    name.endsWith(".xml", true)

                if (isAudio && !isMetadata &&
                    format != "metadata" &&
                    file.optString("source", "") != "derivative") {
                    audioFile = name
                    break
                }
            }

            if (audioFile.isNotBlank()) {
                val safeFile = audioFile
                    .split("/")
                    .joinToString("/") { encodeQuery(it).replace("+", "%20") }

                val stream =
                    "https://archive.org/download/$identifier/$safeFile"

                val title = doc.optString("title", identifier)
                val creator = doc.optString("creator", "Internet Archive")

                results.add(
                    Station(
                        name = "$title — $creator",
                        streamUrl = stream,
                        favicon = "",
                        genre = "Arquivo de áudio",
                        source = "Internet Archive"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return results
}

@Composable
fun StreamingSearchScreen(onStationSelected: (Station) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var stations by remember { mutableStateOf<List<Station>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Pesquise uma música ou artista.") }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    fun searchMusic(query: String) {
        if (query.isBlank()) {
            message = "Digite o nome de uma música ou artista."
            return
        }

        isLoading = true
        stations = emptyList()
        message = "Buscando no Audius e Internet Archive..."

        scope.launch {
            val audiusResults = withContext(Dispatchers.IO) {
                try {
                    searchAudius(query)
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList()
                }
            }

            val archiveResults = withContext(Dispatchers.IO) {
                try {
                    searchInternetArchive(query)
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList()
                }
            }

            stations = audiusResults + archiveResults
            isLoading = false

            message = if (stations.isEmpty()) {
                "Nenhuma faixa encontrada. Tente outro termo."
            } else {
                "${stations.size} resultados encontrados."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("Buscar música ou artista...")
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        searchMusic(searchQuery)
                    }
                ) {
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = "Buscar",
                        tint = Color(0xFFFFB74D)
                    )
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus()
                    searchMusic(searchQuery)
                }
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "FONTES: AUDIUS • INTERNET ARCHIVE",
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = Color(0xFFFFB74D)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFFFB74D))
            }
        } else {
            Text(
                message,
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(stations) { station ->
                    StationRowItem(
                        station = station,
                        onClick = { onStationSelected(station) }
                    )
                }
            }
        }
    }
}

@Composable
fun StationRowItem(
    station: Station,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E1E))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF282828)),
            contentAlignment = Alignment.Center
        ) {
            if (station.favicon.isNotBlank()) {
                AsyncImage(
                    model = station.favicon,
                    contentDescription = "Capa",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    "♪",
                    color = Color(0xFFFFB74D),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                station.name,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${station.source} • ${station.genre}",
                fontSize = 11.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "Tocar",
            tint = Color(0xFFFFB74D)
        )
    }
}
