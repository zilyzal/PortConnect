package com.example.portconnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

// ---------- Model ----------

enum class Status { IDLE, CONNECTING, CONNECTED, FAILED }

data class Target(
    val name: String,
    val host: String,
    val port: Int,
    val status: Status = Status.IDLE,
    val detail: String = "Not connected",
)

// ---------- ViewModel ----------

class MainViewModel : ViewModel() {
    var targets by mutableStateOf(
        listOf(
            Target("Server A", "192.168.100.4", 1453),
            Target("Server B", "192.168.100.4", 34177),
            Target("Server C", "192.168.100.4", 8084),
        )
    )
        private set

    private fun update(index: Int, transform: (Target) -> Target) {
        targets = targets.mapIndexed { i, t -> if (i == index) transform(t) else t }
    }

    fun connect(index: Int) {
        val t = targets[index]
        if (t.status == Status.CONNECTING) return
        viewModelScope.launch { doConnect(index) }
    }

    fun connectAll() {
        viewModelScope.launch {
            targets.indices.map { async { doConnect(it) } }.awaitAll()
        }
    }

    private suspend fun doConnect(index: Int) {
        val t = targets[index]
        update(index) { it.copy(status = Status.CONNECTING, detail = "Connecting…") }
        val start = System.currentTimeMillis()
        val result = withContext(Dispatchers.IO) {
            try {
                Socket().use { it.connect(InetSocketAddress(t.host, t.port), 4000) }
                Result.success(System.currentTimeMillis() - start)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        update(index) {
            result.fold(
                onSuccess = { ms -> it.copy(status = Status.CONNECTED, detail = "Connected · ${ms} ms") },
                onFailure = { e ->
                    it.copy(
                        status = Status.FAILED,
                        detail = e.message?.take(60) ?: e.javaClass.simpleName
                    )
                }
            )
        }
    }
}

// ---------- Activity ----------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PortConnectTheme { PortConnectScreen() } }
    }
}

// ---------- Theme ----------

private val Bg = Color(0xFF0F1115)
private val CardBg = Color(0xFF181B22)
private val Accent = Color(0xFF6C8CFF)
private val Good = Color(0xFF3DDC97)
private val Bad = Color(0xFFFF6B6B)
private val Warn = Color(0xFFFFC857)
private val Muted = Color(0xFF8A93A6)

@Composable
fun PortConnectTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Bg,
            surface = CardBg,
            onBackground = Color(0xFFE8ECF4),
            onSurface = Color(0xFFE8ECF4),
        ),
        content = content
    )
}

// ---------- UI ----------

@Composable
fun PortConnectScreen(vm: MainViewModel = viewModel()) {
    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Text("Port Connect", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Tap a card's button to test its connection", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(24.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(vm.targets.size) { i ->
                    TargetCard(vm.targets[i]) { vm.connect(i) }
                }
            }

            Button(
                onClick = { vm.connectAll() },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Filled.Bolt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Connect all", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun TargetCard(target: Target, onConnect: () -> Unit) {
    val dot by animateColorAsState(
        when (target.status) {
            Status.IDLE -> Muted
            Status.CONNECTING -> Warn
            Status.CONNECTED -> Good
            Status.FAILED -> Bad
        },
        label = "dot"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dot)
                )
                Spacer(Modifier.width(10.dp))
                Text(target.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "${target.host}:${target.port}",
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Text(target.detail, color = dot, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))

            val busy = target.status == Status.CONNECTING
            Button(
                onClick = onConnect,
                enabled = !busy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (target.status == Status.IDLE) "Connect" else "Reconnect")
                }
            }
        }
    }
}
