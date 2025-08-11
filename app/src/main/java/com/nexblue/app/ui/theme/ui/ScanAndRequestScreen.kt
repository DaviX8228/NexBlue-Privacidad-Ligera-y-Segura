package com.nexblue.app.ui.theme.ui

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexblue.app.bluetooth.BluetoothManager
import com.nexblue.app.bluetooth.BluetoothScanner
import com.nexblue.app.bluetooth.BluetoothUtils
import com.nexblue.app.bluetooth.BluetoothDeviceInfo
import com.nexblue.app.data.db.AppDatabase
import com.nexblue.app.data.entities.ChatEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.result.ActivityResultLauncher

@Composable
fun ScanScreen(
    onUserSelected: (String) -> Unit,
    hasBluetoothPermissions: () -> Boolean,
    requestPermissionLauncher: ActivityResultLauncher<Array<String>>,
    enableBluetoothLauncher: ActivityResultLauncher<android.content.Intent>,
    bluetoothPermissions: Array<String>
) {

    val context = LocalContext.current
    val scannedDevices = remember { mutableStateListOf<BluetoothDeviceInfo>() }
    val incomingRequests = remember { mutableStateListOf<BluetoothDeviceInfo>() }

    var isLoading by remember { mutableStateOf(false) }
    var shouldStartScan by remember { mutableStateOf(false) }
    var showAliasDialog by remember { mutableStateOf(false) }
    var selectedRequest by remember { mutableStateOf<BluetoothDeviceInfo?>(null) }
    var aliasText by remember { mutableStateOf("") }
    var debugInfo by remember { mutableStateOf("") }
    var showAppNotInstalledDialog by remember { mutableStateOf(false) }
    var deviceWithoutApp by remember { mutableStateOf<BluetoothDeviceInfo?>(null) }

    var hasPermissions by remember { mutableStateOf(false) }
    var isBluetoothEnabled by remember { mutableStateOf(false) }



    // Callback para manejar mensajes recibidos
    val messageCallback: (String, BluetoothDevice) -> Unit = { message, device ->
        Log.d("ScanScreen", "Mensaje recibido: '$message' de ${device.address}")



        if (message.startsWith("solicitud|")) {
            val senderUserId = message.substringAfter("solicitud|")
            Log.d("ScanScreen", "Procesando solicitud de: $senderUserId")

            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getInstance(context)
                val chatDao = db.chatDao()

                // Verificar si ya existe chat
                val existingChat = chatDao.getChatByUserId(senderUserId)

                if (existingChat == null) {
                    // Verificar duplicados en UI
                    val alreadyInRequests = incomingRequests.any { it.userId == senderUserId }

                    if (!alreadyInRequests) {
                        val alias = device.name ?: "Dispositivo desconocido"

                        // Agregar a la UI en el hilo principal
                        CoroutineScope(Dispatchers.Main).launch {
                            incomingRequests.add(
                                BluetoothDeviceInfo(
                                    userId = senderUserId,
                                    alias = alias,
                                    hasNexBlueApp = true,
                                    bluetoothDevice = device
                                )
                            )
                            Log.d("ScanScreen", "✅ Nueva solicitud agregada: $senderUserId ($alias)")
                        }
                    } else {
                        Log.d("ScanScreen", "Solicitud duplicada ignorada: $senderUserId")
                    }
                } else {
                    Log.d("ScanScreen", "Solicitud ignorada, chat ya existe: $senderUserId")
                }
            }
        }

    }
    val permissionLauncher = rememberUpdatedState(newValue = requestPermissionLauncher)
    LaunchedEffect(hasBluetoothPermissions()) {
        if (hasBluetoothPermissions()) {
            BluetoothManager.initialize(context)
            BluetoothManager.addMessageCallback(messageCallback)
        }
    }



// Solicitar permisos (pero sin inicializar todavía)
    LaunchedEffect(Unit) {
        if (!hasBluetoothPermissions()) {
            requestPermissionLauncher.launch(bluetoothPermissions)
        }
    }
    // Este se ejecutará cuando los permisos estén activos
    LaunchedEffect(hasBluetoothPermissions()) {
        if (hasBluetoothPermissions()) {
            BluetoothManager.initialize(context)
            BluetoothManager.addMessageCallback(messageCallback)
        }
    }



    // Limpiar al salir
    DisposableEffect(Unit) {
        onDispose {
            BluetoothManager.removeMessageCallback(messageCallback)
            BluetoothScanner.stopScan(context)
        }
    }

    // Función para manejar click en dispositivo
    fun handleDeviceClick(device: BluetoothDeviceInfo) {
        if (device.hasNexBlueApp) {
            val message = "solicitud|${BluetoothManager.myUserId}"

            Log.d("ScanScreen", "🚀 ENVIANDO SOLICITUD:")
            Log.d("ScanScreen", "   Mensaje: '$message'")
            Log.d("ScanScreen", "   Mi ID: ${BluetoothManager.myUserId}")
            Log.d("ScanScreen", "   A dispositivo: ${device.alias}")
            Log.d("ScanScreen", "   Dirección: ${device.bluetoothDevice?.address}")

            if (device.bluetoothDevice == null) {
                Log.e("ScanScreen", "❌ BluetoothDevice es null!")
                return
            }

            if (!hasBluetoothPermissions()) {
                requestPermissionLauncher.launch(bluetoothPermissions)
                return
            }

            // Usar el sistema centralizado para enviar
            BluetoothManager.sendMessage(context, device.bluetoothDevice, message)

            // Navegar al chat
            onUserSelected(device.userId)

        } else {
            deviceWithoutApp = device
            showAppNotInstalledDialog = true
        }
    }

    // Verificar permisos y estado
    LaunchedEffect(Unit) {
        hasPermissions = hasBluetoothPermissions()
        isBluetoothEnabled = BluetoothUtils.isBluetoothEnabled()
        debugInfo = "Permisos: $hasPermissions, Bluetooth: $isBluetoothEnabled"

        if (hasPermissions && isBluetoothEnabled) {
            debugInfo += "\n✅ Sistema listo - Mi ID: ${BluetoothManager.myUserId}"
        }
    }

    // Lógica de escaneo
    LaunchedEffect(shouldStartScan) {
        if (shouldStartScan) {
            Log.d("ScanScreen", "Iniciando escaneo...")

            hasPermissions = hasBluetoothPermissions()
            isBluetoothEnabled = BluetoothUtils.isBluetoothEnabled()

            if (!hasPermissions || !isBluetoothEnabled) {
                shouldStartScan = false
                return@LaunchedEffect
            }

            scannedDevices.clear()
            isLoading = true
            debugInfo = "🔍 Buscando dispositivos con NexBlue..."

            BluetoothScanner.startScan(
                context = context,
                onDeviceFound = { device ->
                    if (scannedDevices.none { it.userId == device.userId }) {
                        scannedDevices.add(device)
                    }
                    val devicesWithApp = scannedDevices.count { it.hasNexBlueApp }
                    debugInfo = "📱 Encontrados: ${scannedDevices.size} dispositivos ($devicesWithApp con NexBlue)"
                }
            )

            delay(15000)
            BluetoothScanner.stopScan(context)
            isLoading = false
            shouldStartScan = false

            val devicesWithApp = scannedDevices.count { it.hasNexBlueApp }
            debugInfo = "✅ Escaneo completado: ${scannedDevices.size} dispositivos ($devicesWithApp con NexBlue)"
        }
    }

    // Función para manejar el click del botón de búsqueda
    fun handleSearchClick() {
        hasPermissions = hasBluetoothPermissions()
        isBluetoothEnabled = BluetoothUtils.isBluetoothEnabled()

        when {
            !hasPermissions -> {
                debugInfo = "📋 Solicitando permisos de Bluetooth..."
                requestPermissionLauncher.launch(bluetoothPermissions)
            }
            !isBluetoothEnabled -> {
                debugInfo = "🔵 Solicitando habilitar Bluetooth..."
                val enableBtIntent = android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBluetoothLauncher.launch(enableBtIntent)
            }
            else -> {
                shouldStartScan = true
            }
        }
    }

    // AlertDialog para solicitudes
    if (showAliasDialog && selectedRequest != null) {
        AlertDialog(
            onDismissRequest = {
                showAliasDialog = false
                selectedRequest = null
                aliasText = ""
            },
            title = { Text("Agregar como amigo", color = Color.White) },
            text = {
                Column {
                    Text(
                        text = "¿Cómo quieres que aparezca ${selectedRequest!!.alias} en tu lista de amigos?",
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = aliasText,
                        onValueChange = { aliasText = it },
                        label = { Text("Alias de amigo", color = Color.Gray) },
                        placeholder = { Text(selectedRequest!!.alias, color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF1D9FFD),
                            unfocusedBorderColor = Color.Gray
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        acceptFriendRequest(
                            context = context,
                            request = selectedRequest!!,
                            alias = aliasText,
                            onComplete = {
                                incomingRequests.remove(selectedRequest)
                                showAliasDialog = false
                                selectedRequest = null
                                aliasText = ""
                            }
                        )
                    }
                ) {
                    Text("Aceptar", color = Color(0xFF1D9FFD))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAliasDialog = false
                        selectedRequest = null
                        aliasText = ""
                    }
                ) {
                    Text("Cancelar", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF2D2D2D)
        )
    }

    // AlertDialog para dispositivos sin app
    if (showAppNotInstalledDialog && deviceWithoutApp != null) {
        AlertDialog(
            onDismissRequest = {
                showAppNotInstalledDialog = false
                deviceWithoutApp = null
            },
            title = {
                Text("App no instalada", color = Color.White)
            },
            text = {
                Column {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "El dispositivo '${deviceWithoutApp!!.alias}' no tiene NexBlue instalado.",
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Para poder enviar solicitudes de amistad, el otro usuario debe tener la aplicación instalada.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAppNotInstalledDialog = false
                        deviceWithoutApp = null
                    }
                ) {
                    Text("Entendido", color = Color(0xFF1D9FFD))
                }
            },
            containerColor = Color(0xFF2D2D2D)
        )
    }

    // UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo
        Box(
            modifier = Modifier.size(60.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = com.nexblue.app.R.drawable.isotipo_nexblue),
                contentDescription = null,
                modifier = Modifier.size(50.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Contador con información
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("${scannedDevices.size}", color = Color(0xFF1D9FFD))
            Spacer(modifier = Modifier.width(8.dp))
            Text("(${scannedDevices.count { it.hasNexBlueApp }} con NexBlue)",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Debug info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = debugInfo,
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Estado del sistema
        if (!hasPermissions || !isBluetoothEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF404040)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val (statusText, buttonText) = when {
                        !hasPermissions -> "⚠️ Permisos de Bluetooth requeridos" to "Otorgar permisos"
                        !isBluetoothEnabled -> "📱 Bluetooth deshabilitado" to "Habilitar Bluetooth"
                        else -> "" to ""
                    }

                    Text(
                        text = statusText,
                        color = Color(0xFFFFB74D),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { handleSearchClick() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB74D)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(text = buttonText, color = Color.Black)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Botón de búsqueda
        Button(
            onClick = { handleSearchClick() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (hasPermissions && isBluetoothEnabled) Color(0xFF0207AE) else Color.Gray
            ),
            shape = RoundedCornerShape(25.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                val buttonText = when {
                    !hasPermissions -> "Otorgar permisos"
                    !isBluetoothEnabled -> "Habilitar Bluetooth"
                    else -> "Buscar dispositivos"
                }
                Text(buttonText, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Usuarios disponibles:", color = Color.White, modifier = Modifier.align(Alignment.Start))

        Spacer(modifier = Modifier.height(16.dp))
        Log.d("ScanScreen", "Solicitudes recibidas: ${incomingRequests.size}")

        // Lista de dispositivos con indicadores visuales
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(scannedDevices) { device ->
                val hasApp = device.hasNexBlueApp

                Card(
                    onClick = { handleDeviceClick(device) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Row(
                        modifier = Modifier.padding(5.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(50.dp).background(Color(0xFF404040), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF808080), modifier = Modifier.size(20.dp))
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = device.alias,
                                color = Color(0xFF2196F3),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            // Indicador visual del estado de la app
                            Text(
                                text = if (hasApp) "✅ NexBlue instalado" else "❌ App no detectada",
                                color = if (hasApp) Color(0xFF4CAF50) else Color(0xFF808080),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = if (hasApp) "Enviar solicitud" else "App no instalada",
                            tint = if (hasApp) Color(0xFF1D9FFD) else Color(0xFF808080),
                            modifier = Modifier.size(24.dp)
                                .background(
                                    color = if (hasApp) Color(0xFF1D4ED8) else Color(0xFF404040),
                                    shape = CircleShape
                                )
                                .padding(4.dp)
                        )
                    }
                }
            }

            // Sección de solicitudes entrantes
            if (incomingRequests.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D)),
                        shape = RoundedCornerShape(35.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Solicitudes de mensajes:",
                                color = Color.White,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(15.dp))

                            incomingRequests.forEach { request ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(50.dp).background(Color(0xFF404040), shape = CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF808080), modifier = Modifier.size(20.dp))
                                    }

                                    Spacer(modifier = Modifier.width(15.dp))

                                    Text(
                                        text = request.alias,
                                        color = Color(0xFF2196F3),
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(onClick = {
                                        selectedRequest = request
                                        aliasText = request.alias
                                        showAliasDialog = true
                                    }) {
                                        Icon(Icons.Default.Check, contentDescription = "Aceptar", tint = Color.White)
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    IconButton(onClick = {
                                        incomingRequests.remove(request)
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Rechazar", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (scannedDevices.isEmpty() && !isLoading && hasPermissions && isBluetoothEnabled) {
            Spacer(modifier = Modifier.weight(1f))
            Text("No se encontraron dispositivos", color = Color.Gray)
            Text("Asegúrate de que otros dispositivos tengan NexBlue instalado y Bluetooth visible",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

// Función auxiliar para aceptar solicitudes
private fun acceptFriendRequest(
    context: Context,
    request: BluetoothDeviceInfo,
    alias: String,
    onComplete: () -> Unit
) {
    CoroutineScope(Dispatchers.IO).launch {
        val db = AppDatabase.getInstance(context)
        val chatDao = db.chatDao()

        // Verificar que no exista ya
        val exists = chatDao.getChatByUserId(request.userId)
        if (exists == null) {
            chatDao.insertChat(
                ChatEntity(
                    userId = request.userId,
                    alias = alias.ifBlank { request.alias },
                    lastMessage = "",
                    lastTimestamp = System.currentTimeMillis()
                )
            )
            Log.d("ScanScreen", "✅ Chat creado para: ${request.userId}")
        } else {
            Log.d("ScanScreen", "Chat ya existía para: ${request.userId}")
        }

        // Ejecutar callback en hilo principal
        CoroutineScope(Dispatchers.Main).launch {
            onComplete()
        }
    }
}