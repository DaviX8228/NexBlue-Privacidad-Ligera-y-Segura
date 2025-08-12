package com.nexblue.app.navigation

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.window.SplashScreen
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nexblue.app.bluetooth.BluetoothManager
import com.nexblue.app.data.db.AppDatabase
import com.nexblue.app.ui.theme.ui.BluetoothSetupScreen
import com.nexblue.app.ui.theme.ui.ChatPrivadoScreen
import com.nexblue.app.ui.theme.ui.ChatPublicScreen
import com.nexblue.app.ui.theme.ui.SplashScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    hasBluetoothPermissions: () -> Boolean,
    requestPermissionLauncher: ActivityResultLauncher<Array<String>>,
    enableBluetoothLauncher: ActivityResultLauncher<Intent>,
    context: Context,
    bluetoothPermissions: Array<String>
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.Splash
    ) {
        composable(NavRoutes.Splash) {
            SplashScreen(
                onFinished = {
                    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    val hasPermissions = hasBluetoothPermissions()
                    val isBluetoothOn = bluetoothAdapter?.isEnabled == true
                    val nextRoute = if (hasPermissions && isBluetoothOn) {
                        NavRoutes.ChatPublic
                    } else {
                        NavRoutes.BluetoothSetup
                    }
                    navController.navigate(nextRoute) {
                        popUpTo(NavRoutes.Splash) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.BluetoothSetup) {
            BluetoothSetupScreen(
                context = context,
                bluetoothPermissions = bluetoothPermissions,
                requestPermissionLauncher = requestPermissionLauncher,
                enableBluetoothLauncher = enableBluetoothLauncher,
                navController = navController
            )
        }


        composable(NavRoutes.ChatPublic) {
            ChatPublicScreen(
                context = context,
                onNavigateToPrivateChat = { aliasDestinatario, mensajeInicial ->
                    // Cambio clave: ahora pasamos 2 parámetros
                    navController.navigate(NavRoutes.chatPrivadoWith(aliasDestinatario, mensajeInicial))
                }
            )
        }

        composable(
            route = NavRoutes.ChatPrivado,
            arguments = listOf(
                navArgument("aliasDestinatario") { type = NavType.StringType },
                navArgument("mensajeInicial") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val aliasDestinatario = backStackEntry.arguments?.getString("aliasDestinatario") ?: ""
            val mensajeInicial = backStackEntry.arguments?.getString("mensajeInicial") ?: ""

            ChatPrivadoScreen(
                context = context,
                aliasDestinatario = aliasDestinatario,
                mensajeInicial = mensajeInicial,  // Nuevo parámetro
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}