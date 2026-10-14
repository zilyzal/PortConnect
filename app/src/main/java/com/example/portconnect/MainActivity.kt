package com.example.portconnect

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

// ---------- Palette ----------

private val Ground = Color(0xFF0C1226)
private val Panel = Color(0xFF141C3A)
private val Line = Color(0xFF26315A)
private val Ink = Color(0xFFEAF0FF)
private val Dim = Color(0xFF8C97BC)
private val Accent = Color(0xFF5B86FF)
private val Bad = Color(0xFFFF8A8A)

// ---------- Model ----------

private const val DEFAULT_HOST = "192.168.100.4"

data class Target(
    val name: String,
    val port: Int,
    val accent: Color,
) {
    fun urlFor(host: String): String = "http://$host:$port"
}

private val targets = listOf(
    Target("Blackbox Store", 1453, Color(0xFF7FA6FF)),
    Target("Orbit Store", 34177, Color(0xFFB69CFF)),
    Target("Payload Manager", 8084, Color(0xFF55D6C2)),
    Target("CheatRunner", 9999, Color(0xFFFFC65C)),
    Target("Ps5upload", 9120, Color(0xFFFF8BA7)),
)

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
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }

    Surface(color = Ground, contentColor = Ink, modifier = Modifier.fillMaxSize()) {
        val index = selected
        if (index == null) {
            HomeScreen(
                host = host,
                onSaveHost = { newHost ->
                    prefs.edit().putString("host", newHost).apply()
                    host = newHost
                },
                onOpen = { selected = it }
            )
        } else {
            WebScreen(target = targets[index], host = host, onClose = { selected = null })
        }
    }
}

// ---------- Home ----------

@Composable
fun HomeScreen(host: String, onSaveHost: (String) -> Unit, onOpen: (Int) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    "PortConnect",
                    color = Ink,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.8).sp
                )
                Spacer(Modifier.height(6.dp))
                Text("Pick a service to open it on your network.", color = Dim, fontSize = 15.sp)
                Spacer(Modifier.height(18.dp))
            }
        }

        item { AddressPanel(host = host, onSave = onSaveHost) }

        item {
            Text(
                "Services",
                color = Dim,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp)
            )
        }

        targets.forEachIndexed { i, target ->
            item(key = target.port) {
                ServiceTile(target = target, host = host, onClick = { onOpen(i) })
            }
        }
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
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ground)
                    .border(
                        1.dp,
                        if (valid) Line else Bad,
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Ink,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (changed) normalized?.let(onSave) }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (draft.isEmpty()) {
                                Text(
                                    DEFAULT_HOST,
                                    color = Dim.copy(alpha = 0.55f),
                                    fontSize = 18.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            inner()
                        }
                    }
                )
            }

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
            Text(
                "Enter an address like 192.168.1.20.",
                color = Bad,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun ServiceTile(target: Target, host: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(lerp(Panel, target.accent, 0.13f))
            .border(1.dp, target.accent.copy(alpha = 0.30f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(target.name, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
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
            target.port.toString(),
            color = target.accent,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ---------- Web page ----------

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(target: Target, host: String, onClose: () -> Unit) {
    val url = target.urlFor(host)
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
                Text(target.name, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "$host:${target.port}",
                    color = target.accent,
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
                color = target.accent,
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
