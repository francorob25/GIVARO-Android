package com.givaro.android

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.Executors

private val Background = Color(0xFF090B10)
private val SurfaceDark = Color(0xFF151922)
private val Accent = Color(0xFFFF7A24)
private val TextWhite = Color(0xFFF4F5F7)
private val TextGray = Color(0xFF9299A5)

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val artwork: String?
)

class MainActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GivaroApp(
                onPlayTrack = { track ->
                    playTrack(track)
                }
            )
        }
    }

    private fun playTrack(track: MusicTrack) {
        executor.execute {
            try {
                val streamUrl =
                    "https://api.audius.co/v1/tracks/${track.id}/stream?app_name=GIVARO"

                runOnUiThread {
                    try {
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build()
                            )

                            setDataSource(streamUrl)

                            setOnPreparedListener {
                                it.start()
                            }

                            setOnErrorListener { player, _, _ ->
                                player.release()
                                mediaPlayer = null
                                true
                            }

                            prepareAsync()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        executor.shutdown()
        super.onDestroy()
    }
}

@Composable
fun GivaroApp(
    onPlayTrack: (MusicTrack) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var selectedTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val executor = remember {
        Executors.newSingleThreadExecutor()
    }

    fun searchMusic(query: String) {
        if (query.isBlank()) return

        loading = true
        errorMessage = ""

        executor.execute {
            try {
                val encodedQuery =
                    URLEncoder.encode(query, "UTF-8")

                val url = URL(
                    "https://api.audius.co/v1/tracks/search" +
                        "?query=$encodedQuery&app_name=GIVARO"
                )

                val connection =
                    url.openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val response = connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                connection.disconnect()

                val json = JSONObject(response)
                val data = json.optJSONArray("data")

                val result = mutableListOf<MusicTrack>()

                if (data != null) {
                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)

                        val id = item.optString("id")
                        val title = item.optString("title")
                        val artist = item
                            .optJSONObject("user")
                            ?.optString("name")
                            ?: "Artista desconhecido"

                        val artwork = item
                            .optJSONObject("artwork")
                            ?.optString("150x150")

                        if (id.isNotBlank() && title.isNotBlank()) {
                            result.add(
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

                (this@GivaroAppActivityOrNull())

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

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

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "MUSIC",
                        color = Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Sua música. Seu universo.",
                    color = TextGray,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "DESCUBRA",
                    color = Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

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
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = SurfaceDark,
                        cursorColor = Accent
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        searchMusic(searchText)
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

                Spacer(modifier = Modifier.height(20.dp))

                if (loading) {
                    CircularProgressIndicator(
                        color = Accent
                    )
                }

                if (errorMessage.isNotBlank()) {
                    Text(
                        errorMessage,
                        color = Color.Red
                    )
                }

                Text(
                    text = "RESULTADOS",
                    color = TextGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(tracks) { track ->
                        TrackItem(
                            track = track,
                            onClick = {
                                selectedTrack = track
                                onPlayTrack(track)
                            }
                        )
                    }
                }

                selectedTrack?.let { track ->
                    PlayerBar(
                        track = track
                    )
                }
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
                "♫",
                color = Accent,
                fontSize = 25.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                track.title,
                color = TextWhite,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )

            Text(
                track.artist,
                color = TextGray,
                fontSize = 12.sp,
                maxLines = 1
            )
        }

        Text(
            "▶",
            color = Accent,
            fontSize = 19.sp
        )
    }
}

@Composable
fun PlayerBar(
    track: MusicTrack
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
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "♫",
                color = Accent,
                fontSize = 22.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                "TOCANDO AGORA",
                color = Accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                track.title,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Text(
                track.artist,
                color = TextGray,
                fontSize = 11.sp
            )
        }

        Text(
            "♫",
            color = Accent,
            fontSize = 25.sp
        )
    }
}
