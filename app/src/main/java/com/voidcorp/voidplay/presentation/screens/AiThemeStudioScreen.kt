package com.voidcorp.voidplay.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voidcorp.voidplay.presentation.components.GlassBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiThemeStudioScreen(
    onBackClick: () -> Unit
) {
    var prompt by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Theme Studio") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = com.voidcorp.voidplay.R.drawable.rounded_keyboard_arrow_left_24),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Describe the vibe you want for your player theme.",
                style = MaterialTheme.typography.bodyLarge
            )

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("Text Prompt") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. Cyberpunk Neon, Deep Sea, Sunset Glow") }
            )

            Button(
                onClick = { /* Trigger AI generation */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Generate Palette")
            }

            Spacer(modifier = Modifier.weight(1f))

            GlassBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                blur = 40.dp,
                alpha = 0.4f,
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Theme Preview", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
