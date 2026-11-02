package com.movierecapee.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movierecapee.ui.Panel
import com.movierecapee.ui.theme.Adobe
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = koinViewModel()) {
    val groq by vm.groqKey.collectAsStateWithLifecycle()
    val gem by vm.geminiKey.collectAsStateWithLifecycle()
    val groqAsr by vm.groqAsr.collectAsStateWithLifecycle()
    val groqChat by vm.groqChat.collectAsStateWithLifecycle()
    val gemModel by vm.gemini.collectAsStateWithLifecycle()
    val gemTts by vm.geminiTts.collectAsStateWithLifecycle()
    val gemVoice by vm.geminiVoice.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Adobe.Bg,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Adobe.Panel)
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Panel("API key vault") {
                Text(
                    "Both services have a free tier. Paste a key once: it is stored encrypted on this phone and is still here after you close the app. " +
                        "Groq does speech recognition and translation. Gemini is the automatic backup and can also speak.",
                    color = Adobe.TextDim, fontSize = 13.sp
                )
                KeyField("Groq API key", groq, vm::setGroqKey)
                KeyField("Gemini API key", gem, vm::setGeminiKey)
                Text("Translation also falls back to the free MyMemory service when no key works.", color = Adobe.TextDim, fontSize = 12.sp)
            }
            Panel("AI models (advanced)") {
                Text("Free-tier model names change over time. If a step stops working, update the name here.", color = Adobe.TextDim, fontSize = 12.sp)
                Field("Groq speech model", groqAsr, vm::setGroqAsr)
                Field("Groq translation model", groqChat, vm::setGroqChat)
                Field("Gemini model", gemModel, vm::setGemini)
                Field("Gemini voice model", gemTts, vm::setGeminiTts)
                Field("Gemini voice name", gemVoice, vm::setGeminiVoice)
                OutlinedButton(onClick = vm::resetModels) { Text("Reset to defaults") }
            }
            Panel("About") {
                Text(
                    "Movie-Recapee edits video files that you import from your own storage. Use only footage you own or have permission to use. " +
                        "Nothing leaves your phone except the audio or text sent to the AI service you choose.",
                    color = Adobe.TextDim, fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun KeyField(label: String, value: String, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        OutlinedTextField(
            value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
            modifier = Modifier.weight(1f),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (visible) "Hide key" else "Show key")
                }
            }
        )
        IconButton(onClick = { clipboard.getText()?.text?.let { onChange(it.trim()) } }) {
            Icon(Icons.Filled.ContentPaste, "Paste $label")
        }
    }
    Text(if (value.isBlank()) "Not set" else "Saved on this phone (${value.length} characters)", color = if (value.isBlank()) Adobe.TextDim else Adobe.Green, fontSize = 12.sp)
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}
