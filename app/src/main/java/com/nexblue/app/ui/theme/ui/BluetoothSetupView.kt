package com.nexblue.app.ui.theme.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.navigation.NavHostController
import com.nexblue.app.R
import com.nexblue.app.bluetooth.BluetoothAdvertiser
import com.nexblue.app.bluetooth.BluetoothManager
import com.nexblue.app.bluetooth.BluetoothUtils
import com.nexblue.app.bluetooth.BluetoothUtils.hasBluetoothPermissions
import org.intellij.lang.annotations.JdkConstants

// BluetoothSetupScreen.kt - Versión CORREGIDA (sin BluetoothManager)
@SuppressLint("MissingPermission")
@Composable
fun BluetoothSetupScreen(
    context: Context,
    bluetoothPermissions: Array<String>,
    requestPermissionLauncher: ActivityResultLauncher<Array<String>>,
    enableBluetoothLauncher: ActivityResultLauncher<Intent>,
    navController: NavHostController
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var showCompatibilityDialog by remember { mutableStateOf(false) }


    // ❌ REMOVIDO: BluetoothManager initialization
    // Solo usaremos BLEChatManager para evitar conflictos

    // Diálogo de error
    if (showErrorDialog && errorMessage != null) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Error de configuración") },
            text = { Text(errorMessage ?: "Error desconocido") },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text("Aceptar")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.logo_nexblue),
                contentDescription = "Logo NexBlue",
                modifier = Modifier.size(120.dp)
            )

            // Card
            OutlinedCard(
                border = BorderStroke(1.dp, Color(0xFF0057FF)),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(380.dp),
                shape = RoundedCornerShape(20)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(modifier = Modifier.height(40.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.bluetooth_b_brands_solid_full),
                        contentDescription = "Bluetooth",
                        tint = Color(0xFF202AE4),
                        modifier = Modifier.size(140.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "NexBlue necesita acceso a Bluetooth\npara buscar dispositivos cercanos.\nActívalo para continuar",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF1D9FFD)
                    )
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }

            Spacer(modifier = Modifier.height(60.dp))

            Button(
                onClick = {
                    when {
                        !BluetoothUtils.hasBluetoothPermissions(context) -> {
                            requestPermissionLauncher.launch(bluetoothPermissions)
                        }
                        !BluetoothUtils.isBluetoothEnabled() -> {
                            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            enableBluetoothLauncher.launch(enableBtIntent)
                        }
                        else -> {
                            try {
                                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) {
                                    // Android 12 o menor → mostrar advertencia
                                    showCompatibilityDialog = true
                                } else {
                                    // Android 13+ → navegar directamente
                                    navController.navigate("chat_public")
                                }
                            } catch (e: Exception) {
                                Log.e("BluetoothSetup", "❌ Error navegando: ${e.message}")
                                errorMessage = "Error: ${e.localizedMessage}"
                                showErrorDialog = true
                                navController.navigate("chat_public")
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0057FF),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(50)
            ) {
                Text("Continuar")
            }
        }
    }
    if (showCompatibilityDialog) {
        AlertDialog(
            onDismissRequest = { showCompatibilityDialog = false },
            title = { Text("Compatibilidad limitada", color = Color(0xFF1D9FFD)) },
            text = {
                Text("Tu dispositivo tiene una versión de Android que puede tener ciertas limitaciones al enviar mensajes largos por Bluetooth. \n\nSe recomienda enviar mensajes más cortos para mejor compatibilidad.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCompatibilityDialog = false
                        navController.navigate("chat_public")
                    }
                ) {
                    Text("Entendido")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

}