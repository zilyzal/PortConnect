package com.example.portconnect

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

// ---------- Palette ----------

private val Ground = Color(0xFF0C1226)
private val Panel = Color(0xFF141C3A)
private val Line = Color(0xFF26315A)
private val Ink = Color(0xFFEAF0FF)
private val Dim = Color(0xFF8C97BC)
private val Accent = Color(0xFF5B86FF)
private val Bad = Color(0xFFFF8A8A)

private val TileColors = listOf(
    Color(0xFF7FA6FF),
    Color(0xFFB69CFF),
    Color(0xFF55D6C2),
    Color(0xFFFFC65C),
    Color(0xFFFF8BA7),
    Color(0xFF8BD17C),
    Color(0xFFFF9F6B),
    Color(0xFF6CCBFF),
)

// ---------- Model ----------

private const val DEFAULT_HOST = "192.168.100.4"

/** How far (as a share of the tile width) a tile must be swiped left before it is deleted. */
private const val DELETE_SWIPE_FRACTION = 0.45f

data class Service(
    val id: Long,
    val name: String,
    val port: Int,
    val colorIndex: Int,
) {
    val accent: Color get() = TileColors[colorIndex.mod(TileColors.size)]
    fun urlFor(host: String): String = "http://$host:$port"
}

private val DefaultServices = listOf(
    Service(1, "Blackbox Store", 1453, 0),
    Service(2, "Orbit Store", 34177, 1),
    Service(3, "Payload Manager", 8084, 2),
    Service(4, "CheatRunner", 9999, 3),
    Service(5, "Ps5upload", 9120, 4),
)

private fun loadServices(prefs: SharedPreferences): List<Service> {
    val raw = prefs.getString("services", null) ?: return DefaultServices
    return try {
        val arr = JSONArray(raw)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Service(o.getLong("id"), o.getString("name"), o.getInt("port"), o.optInt("color", i))
        }
    } catch (e: Exception) {
        DefaultServices
    }
}

private fun saveServices(prefs: SharedPreferences, list: List<Service>) {
    val arr = JSONArray()
    list.forEach {
        arr.put(
            JSONObject()
                .put("id", it.id)
                .put("name", it.name)
                .put("port", it.port)
                .put("color", it.colorIndex)
        )
    }
    prefs.edit().putString("services", arr.toString()).apply()
}

/** Returns a clean host, the default when empty, or null when the text isn't a valid address. */
private fun normalizeHost(raw: String): String? {
    var s = raw.trim().removePrefix("http://").removePrefix("https://")
    s = s.substringBefore('/').substringBefore(':').trim()
    if (s.isEmpty()) return DEFAULT_HOST
    val valid = Regex("^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$").matches(s)
    return if (valid) s else null
}

// ---------- Activity ----------

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
fun PortConnectTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Ground,
            surface = Panel,
            onBackground = Ink,
            onSurface = Ink,
        ),
        content = content
    )
}

// ---------- App ----------

@Composable
fun PortConnectApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("portconnect", Context.MODE_PRIVATE) }
    var host by remember { mutableStateOf(prefs.getString("host", null) ?: DEFAULT_HOST) }
    var services by remember { mutableStateOf(loadServices(prefs)) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }

    fun setServices(newList: List<Service>) {
        services = newList
        saveServices(prefs, newList)
    }

    Surface(color = Ground, contentColor = Ink, modifier = Modifier.fillMaxSize()) {
        val current = services.firstOrNull { it.id == selectedId }
        if (current == null) {
            HomeScreen(
                host = host,
                services = services,
                onSaveHost = { newHost ->
                    prefs.edit().putString("host", newHost).apply()
                    host = newHost
                },
                onOpen = { selectedId = it.id },
                onDelete = { s -> setServices(services.filterNot { it.id == s.id }) },
                onAdd = { name, port ->
                    val nextColor = (services.maxOfOrNull { it.colorIndex } ?: -1) + 1
                    setServices(services + Service(System.currentTimeMillis(), name, port, nextColor))
                },
                onRestore = { setServices(DefaultServices) }
            )
        } else {
            WebScreen(service = current, host = host, onClose = { selectedId = null })
        }
    }
}

// ---------- Home ----------

@Composable
fun HomeScreen(
    host: String,
    services: List<Service>,
    onSaveHost: (String) -> Unit,
    onOpen: (Service) -> Unit,
    onDelete: (Service) -> Unit,
    onAdd: (String, Int) -> Unit,
    onRestore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
    ) {
        Text(
            "Pick a server to open",
            color = Ink,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 16.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { AddressPanel(host = host, onSave = onSaveHost) }

            item {
                Column(modifier = Modifier.padding(start = 4.dp, top = 10.dp)) {
                    Text("Services", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (services.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text("Swipe a service to the left to delete it.", color = Dim, fontSize = 13.sp)
                    }
                }
            }

            if (services.isEmpty()) {
                item { EmptyState(onRestore = onRestore) }
            }

            items(services, key = { it.id }) { service ->
                SwipeRow(
                    service = service,
                    host = host,
                    onOpen = { onOpen(service) },
                    onDelete = { onDelete(service) },
                    modifier = Modifier.animateItem()
                )
            }

            item { AddBar(onAdd = onAdd) }
        }
    }
}

@Composable
fun EmptyState(onRestore: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No services yet", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("Add one below, or bring back the originals.", color = Dim, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRestore,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ground)
        ) {
            Text("Restore defaults", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun FieldBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    mono: Boolean = false,
    isError: Boolean = false,
) {
    val shape = RoundedCornerShape(16.dp)
    val family = if (mono) FontFamily.Monospace else FontFamily.Default
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .background(Ground)
            .border(1.dp, if (isError) Bad else Line, shape)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(
                placeholder,
                color = Dim.copy(alpha = 0.55f),
                fontSize = 18.sp,
                fontFamily = family,
                maxLines = 1
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 18.sp, fontFamily = family),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onImeAction() }),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun AddressPanel(host: String, onSave: (String) -> Unit) {
    var draft by rememberSaveable(host) { mutableStateOf(host) }
    val normalized = normalizeHost(draft)
    val valid = normalized != null
    val changed = valid && normalized != host

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Panel)
            .border(1.dp, Line, RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Text("Device address", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FieldBox(
                value = draft,
                onValueChange = { draft = it },
                placeholder = DEFAULT_HOST,
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Uri,
                onImeAction = { if (changed) normalized?.let(onSave) },
                mono = true,
                isError = !valid
            )
            Button(
                onClick = { normalized?.let(onSave) },
                enabled = changed,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Ground,
                    disabledContainerColor = Line,
                    disabledContentColor = Dim
                ),
                modifier = Modifier.height(56.dp)
            ) {
                Text("Save", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(10.dp))
        if (valid) {
            Text(
                "Default is $DEFAULT_HOST. Leave it empty to use the default.",
                color = Dim,
                fontSize = 13.sp
            )
        } else {
            Text("Enter an address like 192.168.1.20.", color = Bad, fontSize = 13.sp)
        }
    }
}

@Composable
fun SwipeRow(
    service: Service,
    host: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentDelete by rememberUpdatedState(onDelete)

    Box(modifier = modifier.fillMaxWidth()) {
        if (offsetX.value < 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Bad.copy(alpha = 0.9f))
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text("Delete", color = Ground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val width = size.width.toFloat()
                                if (offsetX.value <= -width * DELETE_SWIPE_FRACTION) {
                                    offsetX.animateTo(-width, tween(150))
                                    currentDelete()
                                } else {
                                    offsetX.animateTo(0f, tween(150))
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f, tween(150)) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val width = size.width.toFloat()
                            scope.launch {
                                offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-width, 0f))
                            }
                        }
                    )
                }
        ) {
            ServiceTile(service = service, host = host, onClick = onOpen)
        }
    }
}

@Composable
fun ServiceTile(service: Service, host: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val accent = service.accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(lerp(Panel, accent, 0.13f))
            .border(1.dp, accent.copy(alpha = 0.30f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                service.name,
                color = Ink,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                host,
                color = Dim,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            service.port.toString(),
            color = accent,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun AddBar(onAdd: (String, Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current

    val portNumber = port.toIntOrNull()
    val portValid = portNumber != null && portNumber in 1..65535
    val canAdd = name.isNotBlank() && portValid

    fun submit() {
        if (canAdd && portNumber != null) {
            onAdd(name.trim(), portNumber)
            name = ""
            port = ""
            focus.clearFocus()
        }
    }

    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Panel)
            .border(1.dp, Line, shape)
            .padding(18.dp)
    ) {
        Text("Add a service", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FieldBox(
                value = name,
                onValueChange = { name = it.take(30) },
                placeholder = "Name",
                modifier = Modifier.weight(1f),
                imeAction = ImeAction.Next
            )
            FieldBox(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit).take(5) },
                placeholder = "Port",
                modifier = Modifier.width(92.dp),
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
                onImeAction = { submit() },
                mono = true,
                isError = port.isNotEmpty() && !portValid
            )
            Button(
                onClick = { submit() },
                enabled = canAdd,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Ground,
                    disabledContainerColor = Line,
                    disabledContentColor = Dim
                ),
                modifier = Modifier.height(56.dp)
            ) {
                Text("Add", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (port.isNotEmpty() && !portValid) {
            Spacer(Modifier.height(8.dp))
            Text("Port must be a number from 1 to 65535.", color = Bad, fontSize = 13.sp)
        }
    }
}

// ---------- Web page ----------

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(service: Service, host: String, onClose: () -> Unit) {
    val url = service.urlFor(host)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableStateOf(0) }
    var failed by remember { mutableStateOf(false) }

    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onClose()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    service.name,
                    color = Ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$host:${service.port}",
                    color = service.accent,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            IconButton(onClick = {
                failed = false
                webView?.reload()
            }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reload", tint = Ink)
            }
        }

        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                color = service.accent,
                trackColor = Line,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Spacer(Modifier.height(4.dp))
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(
                                view: WebView?, url: String?, favicon: Bitmap?
                            ) {
                                failed = false
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError
                            ) {
                                if (request.isForMainFrame) failed = true
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        loadUrl(url)
                        webView = this
                    }
                },
                onRelease = { it.destroy() }
            )

            if (failed) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Ground)
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Can't open this page",
                        color = Ink,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(url, color = Bad, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Check that your phone is on the same Wi-Fi, the service is running, and the device address is correct.",
                        color = Dim,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            failed = false
                            webView?.loadUrl(url)
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Accent,
                            contentColor = Ground
                        )
                    ) {
                        Text("Try again", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
