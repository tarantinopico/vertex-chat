package com.example.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.AppSettings
import com.example.data.preferences.AuthMode
import com.example.data.security.VaultState
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.AmoledBorder
import com.example.ui.theme.AmoledSurfaceCard
import com.example.ui.theme.AmoledSurfaceElevated
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueLight
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SoftViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

@Composable
fun AuthSettingsScreen(
    settings: AppSettings,
    vaultState: VaultState,
    onOpenDrawer: () -> Unit,
    onBackToChat: () -> Unit,
    onSelectAuthMode: (AuthMode) -> Unit,
    onSaveAndEncryptServiceAccount: (rawJson: String, password: String, region: String, projectId: String) -> Unit,
    onUnlockVault: (password: String) -> Unit,
    onLockVault: () -> Unit,
    onClearVault: () -> Unit,
    onSaveVertexConfig: (projectId: String, region: String, defaultModel: String) -> Unit,
    onSaveCustomGeminiKey: (String) -> Unit,
    onSaveGenerationParams: (includeThoughts: Boolean, budget: String, temp: Float, autoContinue: Boolean, maxSteps: Int) -> Unit
) {
    val context = LocalContext.current
    var rawServiceAccountJson by rememberSaveable { mutableStateOf("") }
    var encryptionPassword by rememberSaveable { mutableStateOf("") }
    var unlockPassword by rememberSaveable { mutableStateOf("") }

    var projectId by rememberSaveable(settings.projectId) { mutableStateOf(settings.projectId) }
    var region by rememberSaveable(settings.region) { mutableStateOf(settings.region) }
    var defaultModel by rememberSaveable(settings.defaultModelId) { mutableStateOf(settings.defaultModelId) }
    var customGeminiKey by rememberSaveable(settings.customGeminiApiKey) { mutableStateOf(settings.customGeminiApiKey) }

    var includeThoughts by rememberSaveable(settings.includeThoughts) { mutableStateOf(settings.includeThoughts) }
    var thinkingBudget by rememberSaveable(settings.thinkingBudget) { mutableStateOf(settings.thinkingBudget) }
    var temperature by rememberSaveable(settings.temperature) { mutableFloatStateOf(settings.temperature) }
    var autoContinue by rememberSaveable(settings.autoContinueMaxTokens) { mutableStateOf(settings.autoContinueMaxTokens) }
    var maxContinueSteps by rememberSaveable(settings.maxAutoContinueSteps) { mutableIntStateOf(settings.maxAutoContinueSteps) }

    val jsonFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                        rawServiceAccountJson = reader.readText()
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = AmoledBlack,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xF2080C17))
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Otevřít menu",
                                tint = TextPrimary
                            )
                        }
                        Column {
                            Text(
                                text = "Autentizace & AI Konfigurace",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Android Keystore AES/GCM • Vertex AI OAuth2",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = ElectricBlueLight
                            )
                        }
                    }

                    TextButton(onClick = onBackToChat) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = ElectricBlueLight,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Do chatu", color = ElectricBlueLight)
                    }
                }
                HorizontalDivider(color = ElectricBlue.copy(alpha = 0.22f))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AmoledBlack)
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Auth Mode Selector Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledSurfaceCard),
                border = BorderStroke(1.dp, AmoledBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = ElectricBlueLight
                        )
                        Text(
                            text = "Režim autentizace AI",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = settings.authMode == AuthMode.VERTEX_SERVICE_ACCOUNT,
                            onClick = { onSelectAuthMode(AuthMode.VERTEX_SERVICE_ACCOUNT) },
                            label = { Text("Vertex AI (Service Account)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricBlue,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mode_vertex_sa_chip")
                        )

                        FilterChip(
                            selected = settings.authMode == AuthMode.GEMINI_API_KEY,
                            onClick = { onSelectAuthMode(AuthMode.GEMINI_API_KEY) },
                            label = { Text("Gemini API Klíč") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SoftViolet,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mode_gemini_key_chip")
                        )
                    }
                }
            }

            // 2. Service Account Keystore Vault Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledSurfaceCard),
                border = BorderStroke(
                    1.dp,
                    when (vaultState) {
                        is VaultState.Unlocked -> EmeraldSuccess.copy(alpha = 0.6f)
                        is VaultState.Locked -> Color(0xFFF59E0B).copy(alpha = 0.6f)
                        is VaultState.Unconfigured -> AmoledBorder
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = when (vaultState) {
                                    is VaultState.Unlocked -> Icons.Default.LockOpen
                                    else -> Icons.Default.Lock
                                },
                                contentDescription = null,
                                tint = when (vaultState) {
                                    is VaultState.Unlocked -> EmeraldSuccess
                                    is VaultState.Locked -> Color(0xFFF59E0B)
                                    is VaultState.Unconfigured -> TextSecondary
                                }
                            )
                            Column {
                                Text(
                                    text = "Šifrovaný trezor Service Accountu",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = when (vaultState) {
                                        is VaultState.Unlocked -> "Odemčeno: ${vaultState.credentials.clientEmail}"
                                        is VaultState.Locked -> "Uzamčeno: ${vaultState.clientEmailHint}"
                                        is VaultState.Unconfigured -> "Zatím nebyl uložen žádný Service Account JSON"
                                    },
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    // If Vault is Locked, show quick Unlock form
                    if (vaultState is VaultState.Locked) {
                        HorizontalDivider(color = AmoledBorder)
                        OutlinedTextField(
                            value = unlockPassword,
                            onValueChange = { unlockPassword = it },
                            label = { Text("Heslo pro odemčení trezoru") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vault_unlock_password_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    onUnlockVault(unlockPassword)
                                    unlockPassword = ""
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("vault_unlock_btn")
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Odemknout OAuth2")
                            }

                            OutlinedButton(
                                onClick = onClearVault,
                                border = BorderStroke(1.dp, CrimsonError.copy(alpha = 0.6f))
                            ) {
                                Icon(
                                    Icons.Default.DeleteForever,
                                    contentDescription = "Smazat trezor",
                                    tint = CrimsonError
                                )
                            }
                        }
                    }

                    // If Vault is Unlocked, show Lock / Clear controls
                    if (vaultState is VaultState.Unlocked) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onLockVault,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Uzamknout relaci")
                            }
                            OutlinedButton(
                                onClick = onClearVault,
                                border = BorderStroke(1.dp, CrimsonError.copy(alpha = 0.6f))
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = CrimsonError)
                            }
                        }
                    }

                    HorizontalDivider(color = AmoledBorder)

                    Text(
                        text = "Nahrát nebo nahradit Service Account JSON",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )

                    OutlinedButton(
                        onClick = { jsonFilePicker.launch(arrayOf("application/json", "text/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("select_sa_json_file_btn"),
                        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = null,
                            tint = ElectricBlueLight
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Vybrat soubor service-account.json ze zařízení", color = ElectricBlueLight)
                    }

                    OutlinedTextField(
                        value = rawServiceAccountJson,
                        onValueChange = { rawServiceAccountJson = it },
                        label = { Text("Nebo vložte obsah Service Account JSON") },
                        placeholder = { Text("{\n  \"type\": \"service_account\",\n  \"project_id\": \"...\"\n}") },
                        maxLines = 6,
                        textStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sa_json_text_input")
                    )

                    OutlinedTextField(
                        value = encryptionPassword,
                        onValueChange = { encryptionPassword = it },
                        label = { Text("Zvolte heslo pro AES-256-GCM zašifrování (min. 4 znaky)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sa_encryption_password_input")
                    )

                    Button(
                        onClick = {
                            onSaveAndEncryptServiceAccount(
                                rawServiceAccountJson,
                                encryptionPassword,
                                region,
                                projectId
                            )
                            rawServiceAccountJson = ""
                            encryptionPassword = ""
                        },
                        enabled = rawServiceAccountJson.isNotBlank() && encryptionPassword.length >= 4,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_encrypt_sa_btn")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Zašifrovat do Keystore a aktivovat OAuth2")
                    }
                }
            }

            // 3. Google Cloud Vertex AI Project & Region Configuration
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledSurfaceCard),
                border = BorderStroke(1.dp, AmoledBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = ElectricBlueLight
                        )
                        Text(
                            text = "Google Cloud Vertex AI Projekt & Region",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }

                    OutlinedTextField(
                        value = projectId,
                        onValueChange = { projectId = it },
                        label = { Text("Google Cloud Project ID") },
                        placeholder = { Text("např. my-vertex-ai-project") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vertex_project_id_input")
                    )

                    OutlinedTextField(
                        value = region,
                        onValueChange = { region = it },
                        label = { Text("Vertex AI Region (např. us-central1, europe-west1, global)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vertex_region_input")
                    )

                    OutlinedTextField(
                        value = defaultModel,
                        onValueChange = { defaultModel = it },
                        label = { Text("Preferovaný výchozí model") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { onSaveVertexConfig(projectId, region, defaultModel) },
                        colors = ButtonDefaults.buttonColors(containerColor = AmoledSurfaceElevated),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Uložit konfiguraci projektu", color = ElectricBlueLight)
                    }
                }
            }

            // 4. Optional Direct Gemini API Key Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledSurfaceCard),
                border = BorderStroke(1.dp, AmoledBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = SoftViolet
                        )
                        Text(
                            text = "Volitelný Gemini API Klíč (pro rychlé testování)",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "Pokud je prázdné, použije se klíč z BuildConfig.GEMINI_API_KEY (panel Secrets v AI Studiu).",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = customGeminiKey,
                        onValueChange = { customGeminiKey = it },
                        label = { Text("Vlastní Gemini API klíč (volitelné)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onSaveCustomGeminiKey(customGeminiKey) },
                        colors = ButtonDefaults.buttonColors(containerColor = AmoledSurfaceElevated),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Uložit Gemini API klíč", color = SoftViolet)
                    }
                }
            }

            // 5. ThinkingConfig & Background Continuation Parameters
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AmoledSurfaceCard),
                border = BorderStroke(1.dp, AmoledBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = ElectricBlueLight
                        )
                        Text(
                            text = "ThinkingConfig & Dlouhé úlohy (MAX_TOKENS)",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Zobrazovat Thought Summary (includeThoughts)", color = TextPrimary)
                            Text(
                                "Zobrazí rozbalovací myšlenkový postup modelu nad odpovědí",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = includeThoughts,
                            onCheckedChange = { includeThoughts = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = ElectricBlue)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automatické pokračování při MAX_TOKENS", color = TextPrimary)
                            Text(
                                "WorkManager automaticky naváže dalším voláním (max $maxContinueSteps×)",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = autoContinue,
                            onCheckedChange = { autoContinue = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldSuccess)
                        )
                    }

                    Column {
                        Text(
                            text = "Teplota generování (Temperature): ${String.format(java.util.Locale.US, "%.2f", temperature)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                        Slider(
                            value = temperature,
                            onValueChange = { temperature = it },
                            valueRange = 0f..1.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricBlue,
                                activeTrackColor = ElectricBlue
                            )
                        )
                    }

                    Button(
                        onClick = {
                            onSaveGenerationParams(
                                includeThoughts,
                                thinkingBudget,
                                temperature,
                                autoContinue,
                                maxContinueSteps
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Uložit parametry generování")
                    }
                }
            }
        }
    }
}
