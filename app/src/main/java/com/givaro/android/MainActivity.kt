package com.givaro.android

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val speechRecognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            spokenText?.let { processVoiceCommand(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Verificação segura para evitar crash se a permissão não estiver concedida
        try {
            checkAndEnableDoNotDisturb()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            GivaroWalkmanTheme {
                WalkmanScreen(
                    onVoiceCommandClick = { startVoiceRecognition() },
                    onToggleDoNotDisturb = { requestDoNotDisturbPermission() }
                )
            }
        }
    }

    private fun checkAndEnableDoNotDisturb() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.isNotificationPolicyAccessGranted) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        }
    }

    private fun requestDoNotDisturbPermission() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Por favor, permita o acesso ao Não Perturbe", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Modo Foco já está ativo!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale um comando (ex: 'Seguinte', 'Anterior', 'Pausar')")
        }
        speechRecognizerLauncher.launch(intent)
    }

    private fun processVoiceCommand(command: String) {
        val cleanCommand = command.lowercase(Locale.ROOT)
        when {
            cleanCommand.contains("seguinte") || cleanCommand.contains("próxima") -> {
                Toast.makeText(this, "Comando: Próxima Música", Toast.LENGTH_SHORT).show()
            }
            cleanCommand.contains("anterior") -> {
                Toast.makeText(this, "Comando: Música Anterior", Toast.LENGTH_SHORT).show()
            }
            cleanCommand.contains("pausar") || cleanCommand.contains("parar") -> {
                Toast.makeText(this, "Comando: Pausar", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Toast.makeText(this, "Comando reconhecido: $command", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun GivaroWalkmanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            primary = Color(0xFFFFD700) // Dourado estilo Sony Walkman Premium
        ),
        content = content
    )
}

@Composable
fun WalkmanScreen(
    onVoiceCommandClick: () -> Unit,
    onToggleDoNotDisturb: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Barra Superior - Status de Foco e Som
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "WALKMAN",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Surface(
                color = Color(0xFF2C2C2C),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.clickable { onToggleDoNotDisturb() }
            ) {
                Text(
                    text = "HQ DIRECT • FOCO",
                    color = Color.LightGray,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Capa da Música / Display Digital
        Surface(
            modifier = Modifier
                .size(260.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = Color.Gray
                )
            }
        }

        // Informações da Faixa
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Givaro Stream Player",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Central Unificada sem Distrações",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }

        // Botão de Comando de Voz Interno
        IconButton(
            onClick = onVoiceCommandClick,
            modifier = Modifier
                .size(56.dp)
                .background(Color(0xFF252525), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Comando de Voz",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        // Controles de Reprodução Táteis
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /* Anterior */ }) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Anterior",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            FloatingActionButton(
                onClick = { isPlaying = !isPlaying },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Tocar",
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(onClick = { /* Seguinte */ }) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Seguinte",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

    
