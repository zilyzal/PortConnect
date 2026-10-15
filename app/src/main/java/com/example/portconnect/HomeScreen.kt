package com.example.portconnect

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** How far (as a share of the tile width) a tile must be swiped left before it is deleted. */
private const val DELETE_SWIPE_FRACTION = 0.45f

private val FieldHeight = 48.dp
private val PanelShape = RoundedCornerShape(24.dp)

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
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { AddressPanel(host = host, onSave = onSaveHost) }

        item {
            Column(modifier = Modifier.padding(start = 4.dp, top = 6.dp)) {
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

@Composable
private fun EmptyState(onRestore: () -> Unit) {
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

// ---------- Device address ----------

@Composable
private fun AddressPanel(host: String, onSave: (String) -> Unit) {
    // The default address is shown as a grey hint, so the box only holds text the user typed.
    var draft by rememberSaveable(host) {
        mutableStateOf(if (host == DEFAULT_HOST) "" else host)
    }
    val normalized = normalizeHost(draft)
    val valid = normalized != null
    val changed = valid && normalized != host

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(Panel)
            .border(1.dp, Line, PanelShape)
            .padding(16.dp)
    ) {
        Text("Device address", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))

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
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Ground,
                    disabledContainerColor = Line,
                    disabledContentColor = Dim
                ),
                modifier = Modifier.height(FieldHeight)
            ) {
                Text("Save", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(8.dp))
        if (valid) {
            Text("Default is $DEFAULT_HOST", color = Dim, fontSize = 13.sp)
        } else {
            Text("Enter an address like 192.168.1.20.", color = Bad, fontSize = 13.sp)
        }
    }
}

// ---------- Services ----------

@Composable
private fun SwipeRow(
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
private fun ServiceTile(service: Service, host: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val accent = service.accent
    val tint = remember(accent) { lerp(Panel, accent, 0.13f) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tint)
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

// ---------- Add a service ----------

@Composable
private fun AddBar(onAdd: (String, Int) -> Unit) {
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(Panel)
            .border(1.dp, Line, PanelShape)
            .padding(16.dp)
    ) {
        Text("Add a service", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                modifier = Modifier.width(88.dp),
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
                onImeAction = { submit() },
                mono = true,
                isError = port.isNotEmpty() && !portValid
            )
            Button(
                onClick = { submit() },
                enabled = canAdd,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Ground,
                    disabledContainerColor = Line,
                    disabledContentColor = Dim
                ),
                modifier = Modifier.height(FieldHeight)
            ) {
                Text("Add", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (port.isNotEmpty() && !portValid) {
            Spacer(Modifier.height(8.dp))
            Text("Port must be a number from 1 to 65535.", color = Bad, fontSize = 13.sp)
        }
    }
}

// ---------- Shared text field ----------

@Composable
private fun FieldBox(
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
    val shape = RoundedCornerShape(14.dp)
    val family = if (mono) FontFamily.Monospace else FontFamily.Default

    Box(
        modifier = modifier
            .height(FieldHeight)
            .clip(shape)
            .background(Ground)
            .border(1.dp, if (isError) Bad else Line, shape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(
                placeholder,
                color = Dim.copy(alpha = 0.55f),
                fontSize = 16.sp,
                fontFamily = family,
                maxLines = 1
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 16.sp, fontFamily = family),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onImeAction() }),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
