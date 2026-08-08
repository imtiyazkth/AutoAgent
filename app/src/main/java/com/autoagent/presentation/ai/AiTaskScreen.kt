package com.autoagent.presentation.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoagent.ai.NaturalLanguageTaskParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiTaskScreen(
    onBack: () -> Unit,
    onTaskParsed: (NaturalLanguageTaskParser.ParsedTask) -> Unit
) {
    val parser = remember { NaturalLanguageTaskParser() }
    var input by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<NaturalLanguageTaskParser.ParsedTask?>(null) }

    val examples = listOf(
        "WhatsApp mein Imtiyaz ko message karo: Hi bhai aaj kaise ho",
        "Chrome mein youtube.com kholo",
        "Telegram mein search karo Kotlin tutorial",
        "Aaj shaam 6 baje Gmail kholo",
        "Open YouTube and search Kotlin tutorial",
        "Send WhatsApp message to Imtiyaz saying: Good morning"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🤖 AI Task Banao", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("💬 Apna command likho",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall)
                    Text("Hindi, English ya dono mein — offline kaam karta hai",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it; parsed = null },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text("WhatsApp mein Imtiyaz ko message karo: Hi bhai")
                        },
                        minLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = { if (input.isNotBlank()) parsed = parser.parse(input) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = input.isNotBlank()
                    ) {
                        Icon(Icons.Filled.AutoAwesome, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Samjho aur Task Banao 🤖")
                    }
                }
            }

            Text("💡 Examples:", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall)
            examples.forEach { ex ->
                AssistChip(
                    onClick = { input = ex; parsed = null },
                    label = { Text(ex, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            parsed?.let { p ->
                Divider()
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (p.confidence > 0.7f)
                            Color(0xFF4CAF50).copy(0.1f)
                        else Color(0xFFFF9800).copy(0.1f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Task: ${p.taskName}", fontWeight = FontWeight.Bold)
                        Text(p.explanation, style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(
                            progress = p.confidence,
                            modifier = Modifier.fillMaxWidth(),
                            color = if (p.confidence > 0.7f) Color(0xFF4CAF50)
                            else Color(0xFFFF9800)
                        )
                        Text("Confidence: ${(p.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall)
                        Text("Steps (${p.steps.size}):",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall)
                        p.steps.forEachIndexed { i, step ->
                            Text("${i+1}. ${step.type.emoji} ${step.description}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { parsed = null; input = "" },
                                modifier = Modifier.weight(1f)
                            ) { Text("Reset") }
                            Button(
                                onClick = { onTaskParsed(p) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Save, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Task Banao ✅")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
