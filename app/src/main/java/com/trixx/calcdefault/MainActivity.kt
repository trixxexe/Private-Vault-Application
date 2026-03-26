package com.trixx.calcdefault

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF1E1E1E)) {
                    CalculatorScreen {
                        // This is what happens when the secret code is entered!
                        Toast.makeText(this, "Secret Vault Triggered!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

@Composable
fun CalculatorScreen(onSecretTriggered: () -> Unit) {
    var displayText by remember { mutableStateOf("") }
    val secretCode = "7777" // Your temporary master password

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        // The Calculator Display Screen
        Text(
            text = displayText.ifEmpty { "0" },
            fontSize = 64.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            textAlign = TextAlign.End
        )

        // The Buttons
        val buttons = listOf(
            listOf("7", "8", "9", "/"),
            listOf("4", "5", "6", "*"),
            listOf("1", "2", "3", "-"),
            listOf("C", "0", "=", "+")
        )

        for (row in buttons) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (btn in row) {
                    Button(
                        onClick = {
                            when (btn) {
                                "C" -> displayText = ""
                                "=" -> {
                                    if (displayText == secretCode) {
                                        onSecretTriggered() // Vault opens!
                                        displayText = ""
                                    } else {
                                        displayText = "Error" // Dummy math logic for now
                                    }
                                }
                                else -> {
                                    if (displayText == "Error") displayText = ""
                                    displayText += btn
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                    ) {
                        Text(text = btn, fontSize = 28.sp, color = Color.White)
                    }
                }
            }
        }
    }
}
