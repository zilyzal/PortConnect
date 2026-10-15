package com.example.portconnect

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Ground.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Ground.toArgb()),
        )
        setContent { PortConnectTheme { PortConnectApp() } }
    }
}

@Composable
fun PortConnectApp() {
    val context = LocalContext.current
    val store = remember {
        ServiceStore(context.getSharedPreferences("portconnect", Context.MODE_PRIVATE))
    }
    var host by remember { mutableStateOf(store.loadHost()) }
    var services by remember { mutableStateOf(store.loadServices()) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }

    fun updateServices(list: List<Service>) {
        services = list
        store.saveServices(list)
    }

    Surface(color = Ground, contentColor = Ink, modifier = Modifier.fillMaxSize()) {
        val selected = services.firstOrNull { it.id == selectedId }
        if (selected == null) {
            HomeScreen(
                host = host,
                services = services,
                onSaveHost = {
                    store.saveHost(it)
                    host = it
                },
                onOpen = { selectedId = it.id },
                onDelete = { service -> updateServices(services.filterNot { it.id == service.id }) },
                onAdd = { name, port ->
                    val color = (services.maxOfOrNull { it.colorIndex } ?: -1) + 1
                    updateServices(services + Service(System.currentTimeMillis(), name, port, color))
                },
                onRestore = { updateServices(DefaultServices) },
            )
        } else {
            WebScreen(service = selected, host = host, onClose = { selectedId = null })
        }
    }
}
