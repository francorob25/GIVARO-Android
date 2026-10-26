 package com.givaro.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF000000) // Preto absoluto (True Black) OLED
            ) {
                PlayerScreen()
            }
        }
    }
}

@Composable
fun PlayerScreen() {
    var isPlaying by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Título Minimalista
        Text(
            text = "GIVARO",
            color = Color.LightGray,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 4.sp,
            modifier = Modifier.padding(top = 16.dp)
        )

        // Visualizador / Capa Central
        Box(
            modifier = Modifier
                .size(260.dp)
                .background(Color(0xFF1A1A1A), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isPlaying) "A Reproduzir" else "Pausado",
                    color = Color(0xFFFF9800),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Faixa Minimalista",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Givaro Sound",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }

        // Controlos e Botão de Play
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Slider(
                value = 0.35f,
                onValueChange = {},
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFF9800),
                    activeTrackColor = Color(0xFFFF9800),
                    inactiveTrackColor = Color(0xFF333333)
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Botão Principal de Play/Pause
            Button(
                onClick = { isPlaying = !isPlaying },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                modifier = Modifier.height(50.dp).width(160.dp),
                shape = CircleShape
            ) {
                Text(
                    text = if (isPlaying) "PAUSA" else "PLAY",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

