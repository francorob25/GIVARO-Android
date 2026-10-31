
package com.givaro.android

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

private val Background = Color(0xFF090B10)
private val SurfaceDark = Color(0xFF171C26)
private val Accent = Color(0xFFFF7928)
private val TextWhite = Color(0xFFF4F5F7)
private val TextGray = Color(0xFF9299A5)

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val artwork: String?
)

class MainActivity : ComponentActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GivaroApp(
                onSearch = { query, callback ->
                    searchMusic(query, callback)
                },
                onPlayTrack = { track, callback ->
                    playTrack(track, callback)
                }
            )
        }
    }

    private fun searchMusic(
        query: String,
        callback: (List<MusicTrack>, String?) -> Unit
    ) {
        executor.execute {
            var connection: HttpURLConnection? = null

            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val address =
                    "https://api.audius.co/v1/tracks/search" +
                    "?query=$encodedQuery&app_name=GIVARO"

                connection =
                    URL(address).openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                if (connection.responseCode !in 200..299) {
                    throw Exception(
                        "Erro HTTP: ${connection.responseCode}"
                    )
                }

                val response = connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                val json = JSONObject(response)
                val data = json.optJSONArray("data")
                val results = mutableListOf<MusicTrack>()

                if (data != null) {
                    for (i in 0 until data.length()) {
                        val item = data.optJSONObject(i) ?: continue
                        val user = item.optJSONObject("user")
                        val artworkObject = item.optJSONObject("artwork")

                        val id = item.optString("id")
                        val title = item.optString("title")
                        val artist = user?.optString("name")
                            ?: "Artista desconhecido"

                        val artwork = artworkObject
                            ?.optString("150x150")
                            ?.takeIf {
                                it.isNotBlank() && it != "null"
                            }

                        if (id.isNotBlank() && title.isNotBlank()) {
                            results.add(
                                MusicTrack(
                                    id = id,
                                    title = title,
                                    artist = artist,
                                    artwork = artwork
                                )
                            )
                        }
                    }
                }

                mainHandler.post {
                    callback(results, null)
                }

            } catch (e: Exception) {
                mainHandler.post {
                    callback(
                        emptyList(),
                        e.message ?: "Erro ao pesquisar músicas."
                    )
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun playTrack(
        track: MusicTrack,
        callback: (String?) -> Unit
    ) {
        executor.execute {
            mainHandler.post {
                try {
                    mediaPlayer?.release()
                    mediaPlayer = null

                    val streamUrl =
                        "https://api.audius.co/v1/tracks/${track.id}/stream" +
                        "?app_name=GIVARO"

                    val player = MediaPlayer()
                    mediaPlayer = player

                    player.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(
                                AudioAttributes.CONTENT_TYPE_MUSIC
                            )
                            .build()
                    )

                    player.setOnPreparedListener {
                        it.start()
                        callback(null)
                    }

                    player.setOnErrorListener { _, _, _ ->
                        callback(
                            "Não foi possível reproduzir esta música."
                        )
                        mediaPlayer?.release()
                        mediaPlayer = null
                        true
                    }

                    player.setDataSource(streamUrl)
                    player.prepareAsync()

                } catch (e: Exception) {
                    mediaPlayer?.release()
                    mediaPlayer = null

                    callback(
                        e.message ?: "Erro ao iniciar a reprodução."
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        executor.shutdownNow()
        super.onDestroy()
    }
}

@Composable
fun GivaroApp(
    onSearch: (
        String,
        (List<MusicTrack>, String?) -> Unit
    ) -> Unit,
    onPlayTrack: (
        MusicTrack,
        (String?) -> Unit
    ) -> Unit
) {
    var searchText by remember { mutableStateOf("") }

    var tracks by remember {
        mutableStateOf<List<MusicTrack>>(emptyList())
    }

    var selectedTrack by remember {
        mutableStateOf<MusicTrack?>(null)
    }

    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(false) }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GIVARO",
                        color = TextWhite,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp
                    )

                    Spacer(Modifier.width(10.dp))

                    Text(
                        text = "MUSIC",
                        color = Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Sua música. Seu universo.",
                    color = TextGray,
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(28.dp))

                Text(
                    text = "DESCUBRA MÚSICAS",
                    color = Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "Buscar músicas ou artistas",
                            color = TextGray
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = SurfaceDark,
                        cursorColor = Accent
                    )
                )

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (searchText.isNotBlank() && !loading) {
                            loading = true
                            message = ""

                            onSearch(searchText) { result, error ->
                                tracks = result
                                loading = false

                                message = error
                                    ?: if (result.isEmpty()) {
                                        "Nenhuma música encontrada."
                                    } else {
                                        ""
                                    }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        "BUSCAR MÚSICA",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "RESULTADOS",
                    color = TextGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Spacer(Modifier.height(10.dp))

                if (loading) {
                    CircularProgressIndicator(
                        color = Accent,
                        modifier = Modifier.size(28.dp)
                    )

                    Spacer(Modifier.height(10.dp))
                }

                if (message.isNotBlank()) {
                    Text(
                        text = message,
                        color = if (message.startsWith("Erro")) {
                            Color(0xFFFF7777)
                        } else {
                            TextGray
                        },
                        fontSize = 13.sp
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = tracks,
                        key = { it.id }
                    ) { track ->

                        TrackItem(
                            track = track,
                            onClick = {
                                selectedTrack = track
                                playing = false
                                message = ""

                                onPlayTrack(track) { error ->
                                    if (error == null) {
                                        playing = true
                                        message = ""
                                    } else {
                                        playing = false
                                        message = error
                                    }
                                }
                            }
                        )
                    }
                }

                selectedTrack?.let { track ->
                    PlayerBar(
                        track = track,
                        playing = playing
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "GIVARO • MUSIC EXPERIENCE",
                    color = TextGray,
                    fontSize = 9.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
fun TrackItem(
    track: MusicTrack,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                SurfaceDark,
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    Color(0xFF252B36),
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "♫",
                color = Accent,
                fontSize = 25.sp
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                color = TextWhite,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )

            Text(
                text = track.artist,
                color = TextGray,
                fontSize = 12.sp,
                maxLines = 1
            )
        }

        Text(
            text = "▶",
            color = Accent,
            fontSize = 19.sp
        )
    }
}

@Composable
fun PlayerBar(
    track: MusicTrack,
    playing: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color(0xFF202631),
                RoundedCornerShape(16.dp)
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    Accent.copy(alpha = 0.15f),
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "♫",
                color = Accent,
                fontSize = 22.sp
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = if (playing) {
                    "TOCANDO AGORA"
                } else {
                    "CARREGANDO"
                },
                color = Accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = track.title,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Text(
                text = track.artist,
                color = TextGray,
                fontSize = 11.sp
            )
        }

        Text(
            text = if (playing) "♫" else "…",
            color = Accent,
            fontSize = 25.sp
        )
    }
}
