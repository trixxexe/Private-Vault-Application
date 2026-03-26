package com.trixx.calcdefault

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

enum class Screen { Loading, SetupMaster, SetupDummy, Calculator, Vault }

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ELITE UPGRADE 1: Anti-Screenshot & Screen Recording Protection
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        
        setContent {
            MaterialTheme { AppNavigator(this) }
        }
    }
}

@Composable
fun AppNavigator(activity: FragmentActivity) {
    val context = LocalContext.current
    
    val masterKey = remember {
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    }
    val securePrefs = remember {
        EncryptedSharedPreferences.create(
            context, "SecureVaultPrefs_v3", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var currentScreen by remember { mutableStateOf(Screen.Loading) }
    var masterPin by remember { mutableStateOf(securePrefs.getString("MASTER_PIN", null)) }
    var dummyPin by remember { mutableStateOf(securePrefs.getString("DUMMY_PIN", null)) }
    var isDummyMode by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (masterPin == null) currentScreen = Screen.SetupMaster
        else if (dummyPin == null) currentScreen = Screen.SetupDummy
        else currentScreen = Screen.Calculator
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
        when (currentScreen) {
            Screen.Loading -> {} 
            Screen.SetupMaster -> SetupScreen("Set Master PIN") { pin ->
                securePrefs.edit().putString("MASTER_PIN", pin).apply()
                masterPin = pin; currentScreen = Screen.SetupDummy
            }
            Screen.SetupDummy -> SetupScreen("Set Dummy PIN") { pin ->
                if (pin == masterPin) Toast.makeText(context, "Must be different from Master!", Toast.LENGTH_SHORT).show()
                else {
                    securePrefs.edit().putString("DUMMY_PIN", pin).apply()
                    dummyPin = pin; currentScreen = Screen.Calculator
                }
            }
            Screen.Calculator -> CalculatorScreen(
                activity = activity, masterPin = masterPin ?: "", dummyPin = dummyPin ?: "",
                onOpenVault = { dummy -> isDummyMode = dummy; currentScreen = Screen.Vault }
            )
            Screen.Vault -> VaultGalleryScreen(
                isDummyMode = isDummyMode, masterKey = masterKey,
                onCloseVault = { currentScreen = Screen.Calculator }
            )
        }
    }
}

@Composable
fun SetupScreen(title: String, onPinSet: (String) -> Unit) {
    val context = LocalContext.current // FIXED: Context grabbed safely outside the click
    var display by remember { mutableStateOf("") }
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Bottom) {
        Text(text = title, fontSize = 24.sp, color = Color.Gray, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), textAlign = TextAlign.Center)
        Text(text = display.ifEmpty { "0" }, fontSize = 56.sp, color = Color.White, modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), textAlign = TextAlign.Center)
        CalcKeypad(onPress = { btn ->
            when (btn) {
                "C" -> display = ""
                "⌫" -> display = display.dropLast(1)
                "=" -> if (display.length >= 4) onPinSet(display) else Toast.makeText(context, "PIN must be 4+ digits", Toast.LENGTH_SHORT).show()
                else -> if (display.length < 8 && display != "Error") display += btn
            }
        }, showBiometric = false)
    }
}

@Composable
fun CalculatorScreen(activity: FragmentActivity, masterPin: String, dummyPin: String, onOpenVault: (Boolean) -> Unit) {
    val context = LocalContext.current
    var display by remember { mutableStateOf("") }
    var previousValue by remember { mutableStateOf("") }
    var currentOperator by remember { mutableStateOf("") }
    var wrongAttempts by remember { mutableIntStateOf(0) }

    // ELITE UPGRADE 2: Biometric Authentication
    val authenticateBiometric = {
        val executor = ContextCompat.getMainExecutor(context)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Vault")
            .setSubtitle("Confirm your identity")
            .setNegativeButtonText("Use PIN")
            .build()
            
        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                display = ""; wrongAttempts = 0; onOpenVault(false) // Opens REAL vault
            }
        })
        biometricPrompt.authenticate(promptInfo)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Bottom) {
        Text(text = display.ifEmpty { "0" }, fontSize = 64.sp, color = Color.White, modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), textAlign = TextAlign.End, maxLines = 1)
        CalcKeypad(onPress = { btn ->
            when (btn) {
                "BIO" -> authenticateBiometric()
                "C" -> { display = ""; previousValue = ""; currentOperator = "" }
                "⌫" -> display = display.dropLast(1)
                "+", "-", "*", "/" -> {
                    if (display.isNotEmpty() && display != "Error") {
                        previousValue = display; currentOperator = btn; display = ""
                    }
                }
                "=" -> {
                    if (display == masterPin) { display = ""; wrongAttempts = 0; onOpenVault(false) }
                    else if (display == dummyPin) { display = ""; wrongAttempts = 0; onOpenVault(true) }
                    else if (previousValue.isNotEmpty() && display.isNotEmpty()) {
                        try {
                            val v1 = previousValue.toDouble(); val v2 = display.toDouble()
                            if (currentOperator == "/" && v2 == 0.0) { display = "Error"; return@CalcKeypad }
                            val res = when (currentOperator) {
                                "+" -> v1 + v2; "-" -> v1 - v2; "*" -> v1 * v2; "/" -> v1 / v2
                                else -> 0.0
                            }
                            display = if (res % 1 == 0.0) res.toLong().toString() else res.toString()
                            previousValue = ""; currentOperator = ""
                        } catch (e: Exception) { display = "Error" }
                    } else {
                        // ELITE UPGRADE 3: The Fake Crash
                        wrongAttempts++
                        if (wrongAttempts >= 3) {
                            Toast.makeText(context, "Calculator has stopped responding.", Toast.LENGTH_LONG).show()
                            activity.finishAffinity() // Violently closes the app
                        } else if (display.isNotEmpty()) display = "Wrong PIN"
                    }
                }
                else -> {
                    if (display == "Error" || display == "Wrong PIN") display = ""
                    if (display.length < 15) display += btn
                }
            }
        }, showBiometric = true)
    }
}

@Composable
fun CalcKeypad(onPress: (String) -> Unit, showBiometric: Boolean) {
    val buttons = listOf(
        listOf("C", "⌫", if (showBiometric) "BIO" else "", "/"), 
        listOf("7", "8", "9", "*"), 
        listOf("4", "5", "6", "-"), 
        listOf("1", "2", "3", "+"),
        listOf("0", "00", ".", "=")
    )
    for (row in buttons) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (btn in row) {
                if (btn.isEmpty()) { Spacer(modifier = Modifier.weight(1f)); continue }
                
                Button(
                    onClick = { onPress(btn) },
                    modifier = Modifier.weight(1f).aspectRatio(if (btn == "0") 2.1f else 1f),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = if (btn == "=" || btn == "C" || btn == "⌫") Color(0xFF4CAF50) else if (btn == "BIO") Color(0xFF2196F3) else if (btn in listOf("/", "*", "-", "+")) Color(0xFF333333) else Color(0xFF1E1E1E))
                ) {
                    if (btn == "BIO") Icon(Icons.Filled.Lock, contentDescription = "Biometric", tint = Color.White) // FIXED: Replaced missing Fingerprint icon with standard Lock
                    else Text(text = btn, fontSize = 24.sp, color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class) // FIXED: Told compiler to allow swipeable gallery
@Composable
fun VaultGalleryScreen(isDummyMode: Boolean, masterKey: MasterKey, onCloseVault: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val vaultDir = File(context.filesDir, if (isDummyMode) "dummy_files_v3" else "real_files_v3").apply { mkdirs() }
    var files by remember { mutableStateOf(vaultDir.listFiles()?.toList() ?: emptyList()) }
    var isImporting by remember { mutableStateOf(false) }
    
    // State for Fullscreen Viewer
    var selectedImageIndex by remember { mutableStateOf<Int?>(null) }

    // Clear memory cache when closing vault
    BackHandler {
        if (selectedImageIndex != null) {
            selectedImageIndex = null // Close fullscreen first
        } else {
            context.cacheDir.listFiles()?.forEach { it.delete() } // Wipe temporary decrypted files
            onCloseVault()
        }
    }

    // ELITE UPGRADE 4: Forensic Secure Wipe
    val secureDelete = { targetFile: File ->
        try {
            RandomAccessFile(targetFile, "rw").use { raf ->
                raf.write(ByteArray(raf.length().toInt())) // Overwrite with zeroes
            }
            targetFile.delete()
        } catch (e: Exception) { /* Ignore */ }
    }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            isImporting = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val mime = context.contentResolver.getType(uri)
                    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
                    val destFile = File(vaultDir, "ENC_${System.currentTimeMillis()}.$ext")
                    
                    val encryptedFile = EncryptedFile.Builder(
                        context, destFile, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                    ).build()

                    context.contentResolver.openInputStream(uri)?.use { input ->
                        encryptedFile.openFileOutput().use { output -> input.copyTo(output) }
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        files = vaultDir.listFiles()?.toList() ?: emptyList()
                        isImporting = false
                    }
                }
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            if (selectedImageIndex == null) {
                FloatingActionButton(onClick = { pickMedia.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, containerColor = Color(0xFF4CAF50)) {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                }
            }
        },
        containerColor = Color(0xFF0A0A0A)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (selectedImageIndex == null) {
                // GALLERY GRID
                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    Text(text = "Gallery", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                    if (isImporting) Text("Encrypting file...", color = Color.Green)
                    else if (files.isEmpty()) Text("No files here yet.", color = Color.Gray)
                    else {
                        LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            itemsIndexed(files) { index, file ->
                                // ELITE UPGRADE 5: OOM Fix via Cache Streaming
                                var tempImagePath by remember { mutableStateOf<File?>(null) }
                                
                                LaunchedEffect(file) {
                                    withContext(Dispatchers.IO) {
                                        val tempFile = File(context.cacheDir, "temp_${file.name}")
                                        if (!tempFile.exists()) {
                                            try {
                                                val encryptedFile = EncryptedFile.Builder(context, file, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
                                                encryptedFile.openFileInput().use { input ->
                                                    tempFile.outputStream().use { output -> input.copyTo(output) }
                                                }
                                            } catch (e: Exception) { /* skip */ }
                                        }
                                        tempImagePath = tempFile
                                    }
                                }

                                if (tempImagePath != null) {
                                    AsyncImage(
                                        model = tempImagePath,
                                        contentDescription = "Encrypted Image",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.aspectRatio(1f).background(Color.DarkGray)
                                            .clickable { selectedImageIndex = index }
                                            .pointerInput(Unit) {
                                                detectTapGestures(onLongPress = {
                                                    secureDelete(file)
                                                    files = vaultDir.listFiles()?.toList() ?: emptyList()
                                                    Toast.makeText(context, "Securely Wiped", Toast.LENGTH_SHORT).show()
                                                })
                                            }
                                    )
                                } else Box(modifier = Modifier.aspectRatio(1f).background(Color.DarkGray))
                            }
                        }
                    }
                }
            } else {
                // ELITE UPGRADE 6: Fullscreen Pager View
                val pagerState = rememberPagerState(initialPage = selectedImageIndex!!, pageCount = { files.size })
                
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().background(Color.Black)) { page ->
                    val file = files[page]
                    var fullImagePath by remember { mutableStateOf<File?>(null) }
                    
                    LaunchedEffect(file) {
                        withContext(Dispatchers.IO) {
                            val tempFile = File(context.cacheDir, "temp_${file.name}")
                            fullImagePath = tempFile // Assumes grid already decrypted it to cache
                        }
                    }
                    
                    if (fullImagePath != null) {
                        AsyncImage(
                            model = fullImagePath,
                            contentDescription = "Fullscreen Image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
