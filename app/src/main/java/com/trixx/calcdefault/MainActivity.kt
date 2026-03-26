package com.trixx.calcdefault

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Define all the possible screens in our app
enum class Screen {
    Loading, SetupMaster, SetupDummy, Calculator, RealVault, DummyVault
}

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
    val prefs = context.getSharedPreferences("VaultPrefs", Context.MODE_PRIVATE)
    
    var currentScreen by remember { mutableStateOf(Screen.Loading) }
    var masterPin by remember { mutableStateOf(prefs.getString("MASTER_PIN", null)) }
    var dummyPin by remember { mutableStateOf(prefs.getString("DUMMY_PIN", null)) }

    // Check if pins are set up when the app starts
    LaunchedEffect(Unit) {
        if (masterPin == null) {
            currentScreen = Screen.SetupMaster
        } else if (dummyPin == null) {
            currentScreen = Screen.SetupDummy
        } else {
            currentScreen = Screen.Calculator
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
        when (currentScreen) {
            Screen.Loading -> {} // Blank screen while checking prefs
            Screen.SetupMaster -> {
                SetupScreen(
                    title = "Set Master PIN",
                    subtitle = "Type a PIN and press '='",
                    onPinSet = { pin ->
                        prefs.edit().putString("MASTER_PIN", pin).apply()
                        masterPin = pin
                        currentScreen = Screen.SetupDummy
                    }
                )
            }
            Screen.SetupDummy -> {
                SetupScreen(
                    title = "Set Dummy PIN",
                    subtitle = "Used if forced to open vault. Press '='",
                    onPinSet = { pin ->
                        if (pin == masterPin) {
                            // Prevent using the same PIN
                        } else {
                            prefs.edit().putString("DUMMY_PIN", pin).apply()
                            dummyPin = pin
                            currentScreen = Screen.Calculator
                        }
                    }
                )
            }
            Screen.Calculator -> {
                CalculatorScreen(
                    masterPin = masterPin ?: "",
                    dummyPin = dummyPin ?: "",
                    onOpenRealVault = { currentScreen = Screen.RealVault },
                    onOpenDummyVault = { currentScreen = Screen.DummyVault }
                )
            }
            Screen.RealVault -> {
                VaultScreen(
                    title = "🔒 REAL VAULT",
                    color = Color(0xFF00FF00),
                    onCloseVault = { currentScreen = Screen.Calculator }
                )
            }
            Screen.DummyVault -> {
                VaultScreen(
                    title = "🔓 DUMMY VAULT",
                    color = Color(0xFFFFA500),
                    onCloseVault = { currentScreen = Screen.Calculator }
                )
            }
        }
    }
}

@Composable
fun SetupScreen(title: String, subtitle: String, onPinSet: (String) -> Unit) {
    var display by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Bottom) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, fontSize = 28.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Text(text = subtitle, fontSize = 16.sp, color = Color.Gray)
        }
        
        Text(
            text = display.ifEmpty { "0" },
            fontSize = 56.sp, color = Color.White, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), textAlign = TextAlign.Center
        )

        CalcKeypad(
            onPress = { btn ->
                when (btn) {
                    "C" -> display = ""
                    "=" -> if (display.isNotEmpty()) onPinSet(display)
                    else -> display += btn
                }
            },
            disableMath = true // Only allow numbers during setup
        )
    }
}

@Composable
fun CalculatorScreen(masterPin: String, dummyPin: String, onOpenRealVault: () -> Unit, onOpenDummyVault: () -> Unit) {
    var display by remember { mutableStateOf("") }
    var previousValue by remember { mutableStateOf("") }
    var currentOperator by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Bottom) {
        Text(
            text = display.ifEmpty { "0" },
            fontSize = 64.sp, color = Color.White, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), textAlign = TextAlign.End, maxLines = 1
        )

        CalcKeypad(onPress = { btn ->
            when (btn) {
                "C" -> { display = ""; previousValue = ""; currentOperator = "" }
                "+", "-", "*", "/" -> {
                    if (display.isNotEmpty()) {
                        previousValue = display
                        currentOperator = btn
                        display = ""
                    }
                }
                "=" -> {
                    when (display) {
                        masterPin -> { display = ""; onOpenRealVault() }
                        dummyPin -> { display = ""; onOpenDummyVault() }
                        else -> {
                            // Calculate actual math!
                            if (previousValue.isNotEmpty() && display.isNotEmpty()) {
                                try {
                                    val val1 = previousValue.toDouble()
                                    val val2 = display.toDouble()
                                    val result = when (currentOperator) {
                                        "+" -> val1 + val2
                                        "-" -> val1 - val2
                                        "*" -> val1 * val2
                                        "/" -> if (val2 != 0.0) val1 / val2 else Double.NaN
                                        else -> 0.0
                                    }
                                    display = if (result % 1 == 0.0) result.toLong().toString() else result.toString()
                                    previousValue = ""
                                    currentOperator = ""
                                } catch (e: Exception) {
                                    display = "Error"
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (display == "Error") display = ""
                    display += btn
                }
            }
        })
    }
}

@Composable
fun CalcKeypad(onPress: (String) -> Unit, disableMath: Boolean = false) {
    val buttons = listOf(
        listOf("7", "8", "9", "/"),
        listOf("4", "5", "6", "*"),
        listOf("1", "2", "3", "-"),
        listOf("C", "0", "=", "+")
    )

    for (row in buttons) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (btn in row) {
                val isMathBtn = btn in listOf("/", "*", "-", "+")
                if (disableMath && isMathBtn) {
                    Spacer(modifier = Modifier.weight(1f)) // Empty space for disabled math buttons in setup
                    continue
                }
                
                Button(
                    onClick = { onPress(btn) },
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (btn == "=" || btn == "C") Color(0xFF4CAF50) else if (isMathBtn) Color(0xFF333333) else Color(0xFF1E1E1E)
                    )
                ) {
                    Text(text = btn, fontSize = 28.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun VaultScreen(title: String, color: Color, onCloseVault: () -> Unit) {
    BackHandler { onCloseVault() }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(text = title, fontSize = 24.sp, color = color, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Your hidden files will appear here.", color = Color.Gray)
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onCloseVault, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
            Text("Lock & Exit")
        }
    }
}
