package com.trixx.calcdefault

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream

enum class Screen { Loading, SetupMaster, SetupDummy, Calculator, Vault }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppNavigator()
            }
        }
    }
}

@Composable
fun AppNavigator() {
    val context = LocalContext.current
    
    // Pro-Level: AES-256 Encrypted SharedPreferences
    val masterKey = remember {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    val securePrefs = remember {
        EncryptedSharedPreferences.create(
            context,
            "SecureVaultPrefs_v1",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var currentScreen by remember { mutableStateOf(Screen.Loading) }
    var masterPin by remember { mutableStateOf(securePrefs.getString("MASTER_PIN", null)) }
    var dummyPin by remember { mutableStateOf(securePrefs.getString("DUMMY_PIN", null)) }
    var isDummyMode by remember { mutableStateOf(false) } // Tracks which vault we are in

    LaunchedEffect(Unit) {
        if (masterPin == null) currentScreen = Screen.SetupMaster
        else if (dummyPin == null) currentScreen = Screen.SetupDummy
        else currentScreen = Screen.Calculator
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
        when (currentScreen) {
            Screen.Loading -> {} 
            Screen.SetupMaster -> {
                SetupScreen(title = "Set Master PIN", onPinSet = { pin ->
                    securePrefs.edit().putString("MASTER_PIN", pin).apply()
                    masterPin = pin
                    currentScreen = Screen.SetupDummy
                })
            }
            Screen.SetupDummy -> {
                SetupScreen(title = "Set Dummy PIN", onPinSet = { pin ->
                    if (pin == masterPin) {
                        Toast.makeText(context, "Must be different from Master PIN", Toast.LENGTH_SHORT).show()
                    } else {
                        securePrefs.edit().putString("DUMMY_PIN", pin).apply()
                        dummyPin = pin
                        currentScreen = Screen.Calculator
                    }
                })
            }
            Screen.Calculator -> {
                CalculatorScreen(
                    masterPin = masterPin ?: "",
                    dummyPin = dummyPin ?: "",
                    onOpenVault = { dummy -> 
                        isDummyMode = dummy
                        currentScreen = Screen.Vault 
                    }
                )
            }
            Screen.Vault -> {
                VaultGalleryScreen(
                    isDummyMode = isDummyMode,
                    onCloseVault = { currentScreen = Screen.Calculator }
                )
            }
        }
    }
}

@Composable
fun SetupScreen(title: String, onPinSet: (String) -> Unit) {
    var display by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Bottom) {
        Text(text = title, fontSize = 24.sp, color = Color.Gray, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), textAlign = TextAlign.Center)
        Text(text = display.ifEmpty { "0" }, fontSize = 56.sp, color = Color.White, modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), textAlign = TextAlign.Center)
        CalcKeypad(onPress = { btn ->
            when (btn) {
                "C" -> display = ""
                "=" -> if (display.length >= 4) onPinSet(display) else display = "Error"
                else -> if (display.length < 8 && display != "Error") display += btn
            }
        }, disableMath = true)
    }
}

@Composable
fun CalculatorScreen(masterPin: String, dummyPin: String, onOpenVault: (Boolean) -> Unit) {
    var display by remember { mutableStateOf("") }
    var previousValue by remember { mutableStateOf("") }
    var currentOperator by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Bottom) {
        Text(text = display.ifEmpty { "0" }, fontSize = 64.sp, color = Color.White, modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), textAlign = TextAlign.End, maxLines = 1)
        CalcKeypad(onPress = { btn ->
            when (btn) {
                "C" -> { display = ""; previousValue = ""; currentOperator = "" }
                "+", "-", "*", "/" -> {
                    if (display.isNotEmpty() && display != "Error") {
                        previousValue = display; currentOperator = btn; display = ""
                    }
                }
                "=" -> {
                    if (display == masterPin) { display = ""; onOpenVault(false) }
                    else if (display == dummyPin) { display = ""; onOpenVault(true) }
                    else if (previousValue.isNotEmpty() && display.isNotEmpty()) {
                        try {
                            val v1 = previousValue.toDouble(); val v2 = display.toDouble()
                            val res = when (currentOperator) {
                                "+" -> v1 + v2; "-" -> v1 - v2; "*" -> v1 * v2
                                "/" -> if (v2 != 0.0) v1 / v2 else Double.NaN
                                else -> 0.0
                            }
                            display = if (res.isNaN()) "Error" else if (res % 1 == 0.0) res.toLong().toString() else res.toString()
                            previousValue = ""; currentOperator = ""
                        } catch (e: Exception) { display = "Error" }
                    }
                }
                else -> {
                    if (display == "Error") display = ""
                    if (display.length < 15) display += btn
                }
            }
        })
    }
}

@Composable
fun CalcKeypad(onPress: (String) -> Unit, disableMath: Boolean = false) {
    val buttons = listOf(listOf("7", "8", "9", "/"), listOf("4", "5", "6", "*"), listOf("1", "2", "3", "-"), listOf("C", "0", "=", "+"))
    for (row in buttons) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (btn in row) {
                val isMathBtn = btn in listOf("/", "*", "-", "+")
                if (disableMath && isMathBtn) { Spacer(modifier = Modifier.weight(1f)); continue }
                Button(onClick = { onPress(btn) }, modifier = Modifier.weight(1f).aspectRatio(1f), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = if (btn == "=" || btn == "C") Color(0xFF4CAF50) else if (isMathBtn) Color(0xFF333333) else Color(0xFF1E1E1E))) {
                    Text(text = btn, fontSize = 28.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun VaultGalleryScreen(isDummyMode: Boolean, onCloseVault: () -> Unit) {
    BackHandler { onCloseVault() }
    val context = LocalContext.current
    
    // Separate folders so the dummy vault and real vault don't mix!
    val vaultFolderName = if (isDummyMode) "dummy_files" else "real_files"
    val vaultDir = File(context.filesDir, vaultFolderName).apply { mkdirs() }
    
    var files by remember { mutableStateOf(vaultDir.listFiles()?.toList() ?: emptyList()) }

    // This launches the Android Photo Picker
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val fileName = "IMG_${System.currentTimeMillis()}.jpg"
            val destFile = File(vaultDir, fileName)
            
            // Copy the file from public gallery to our hidden internal storage
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            // Refresh the gallery grid
            files = vaultDir.listFiles()?.toList() ?: emptyList()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { pickMedia.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                containerColor = Color(0xFF4CAF50)
            ) { Icon(Icons.Filled.Add, contentDescription = "Add") }
        },
        containerColor = Color(0xFF0A0A0A)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(8.dp)) {
            // No more "Dummy Vault" labels. It just looks like a gallery.
            Text(text = "Gallery", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
            
            if (files.isEmpty()) {
                Text("No files here yet.", color = Color.Gray)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(files) { file ->
                        AsyncImage(
                            model = file,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.aspectRatio(1f).background(Color.DarkGray)
                        )
                    }
                }
            }
        }
    }
}
