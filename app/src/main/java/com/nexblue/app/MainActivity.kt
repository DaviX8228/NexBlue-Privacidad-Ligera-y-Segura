package com.nexblue.app

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.nexblue.app.navigation.AppNavHost
import com.nexblue.app.ui.theme.NexBlueTheme
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.nexblue.app.bluetooth.BluetoothUtils
import com.nexblue.app.navigation.AppNavHost
import com.nexblue.app.ui.theme.NexBlueTheme

class MainActivity : ComponentActivity() {
    // Permisos Bluetooth
    private val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }


    // BroadcastReceiver para eventos Bluetooth
    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        Log.d("BluetoothReceiver", "Dispositivo encontrado: ${it.name ?: "Sin nombre"} - ${it.address}")
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> Log.d("BluetoothReceiver", "Búsqueda Bluetooth iniciada")
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> Log.d("BluetoothReceiver", "Búsqueda Bluetooth finalizada")
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    when (state) {
                        BluetoothAdapter.STATE_ON -> Log.d("BluetoothReceiver", "Bluetooth activado")
                        BluetoothAdapter.STATE_OFF -> Log.d("BluetoothReceiver", "Bluetooth desactivado")
                    }
                }
            }
        }
    }

    private fun hasBluetoothPermissions(context: Context): Boolean {
        val result = BluetoothUtils.hasBluetoothPermissions(context)
        Log.d("MainActivity", "🔍 Verificación de permisos desde MainActivity: $result")
        return result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Registrar el BroadcastReceiver
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        registerReceiver(bluetoothReceiver, filter)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val context = LocalContext.current
            val activity = context as ComponentActivity
            val navController = rememberNavController()

            // Lanzador de permisos
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val allGranted = permissions.all { it.value }
                if (allGranted) {
                    Log.d("MainActivity", "Todos los permisos concedidos")
                } else {
                    Toast.makeText(
                        context,
                        "Los permisos son necesarios para la funcionalidad Bluetooth",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            // Lanzador para habilitar Bluetooth
            val enableBluetoothLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    Log.d("MainActivity", "Bluetooth habilitado")
                }
            }

            // Lanzar permisos en cuanto se inicia
            LaunchedEffect(Unit) {
                val notGranted = bluetoothPermissions.any {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }
                if (notGranted) {
                    permissionLauncher.launch(bluetoothPermissions)
                }
            }

            NexBlueTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentWindowInsets = WindowInsets.systemBars
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        AppNavHost(
                            navController = navController,
                            hasBluetoothPermissions = { hasBluetoothPermissions(context) },
                            requestPermissionLauncher = permissionLauncher,
                            enableBluetoothLauncher = enableBluetoothLauncher,
                            context = context,
                            bluetoothPermissions = bluetoothPermissions
                        )
                    }
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothReceiver)
        BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
    }
}
