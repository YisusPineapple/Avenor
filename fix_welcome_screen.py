import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Add WelcomeScreen composable
welcome_screen_code = """
@Composable
fun WelcomeScreen(appSettings: AppSetting, onFinishSetup: (AppSetting) -> Unit) {
    var selectedProfile by remember { mutableStateOf("VIVID") }
    var selectedResolution by remember { mutableStateOf("ORIGINAL") }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Welcome to Nexo Audio", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Let's configure your initial audio and performance experience.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text("Performance Profile", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("ECO", "BALANCED", "VIVID").forEach { profile ->
                FilterChip(
                    selected = selectedProfile == profile,
                    onClick = { 
                        selectedProfile = profile 
                        selectedResolution = when (profile) {
                            "ECO" -> "LOW"
                            "BALANCED" -> "MEDIUM"
                            else -> "ORIGINAL"
                        }
                    },
                    label = { Text(profile) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Album Art Resolution", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(selectedResolution, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = {
                onFinishSetup(appSettings.copy(
                    performanceMode = selectedProfile,
                    albumArtResolution = selectedResolution,
                    isFirstLaunch = false
                ))
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Start Listening")
        }
    }
}
"""

if "fun WelcomeScreen(" not in content:
    content = content.replace("// ---------------------------------------------------------\n// Core Screens", welcome_screen_code + "\n// ---------------------------------------------------------\n// Core Screens")


# Update NexoAppRoot to intercept isFirstLaunch and Background State Saving
old_root = """@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexoAppRoot(viewModel: PlaybackViewModel = viewModel()) {
val context = LocalContext.current
val activity = context as? MainActivity
var hasPermission by remember { mutableStateOf(false) }

val currentScreen by viewModel.currentScreen.collectAsState()"""

new_root = """@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexoAppRoot(viewModel: PlaybackViewModel = viewModel()) {
val context = LocalContext.current
val activity = context as? MainActivity
var hasPermission by remember { mutableStateOf(false) }

val appSettings by viewModel.appSettings.collectAsState(initial = AppSetting())
val isFirstLaunch = appSettings?.isFirstLaunch ?: false

// Background State Saving
val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
        if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
            // App went to background, trigger persistent queue state save
            viewModel.saveCurrentQueueState()
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
        lifecycleOwner.lifecycle.removeObserver(observer)
    }
}

val currentScreen by viewModel.currentScreen.collectAsState()

if (isFirstLaunch && appSettings != null) {
    WelcomeScreen(appSettings = appSettings!!) { updatedSettings ->
        viewModel.saveSettings(updatedSettings)
    }
    return
}
"""

content = content.replace(old_root, new_root)


# Add Settings UI for Album Art Resolution
old_settings = """val themes = listOf("WARMTH" to "Warmth", "AURORA" to "Aurora", "SOFT_UI" to "Soft UI", "EXPRESSIVE" to "Expressive")"""

new_settings = """
            Text("Album Art Resolution", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)), shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Higher resolutions use more RAM.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    val resolutions = listOf("LOW", "MEDIUM", "HIGH", "ORIGINAL")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        resolutions.forEach { res ->
                            FilterChip(
                                selected = settings.albumArtResolution == res,
                                onClick = { viewModel.saveSettings(settings.copy(albumArtResolution = res)) },
                                label = { Text(res) }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            val themes = listOf("WARMTH" to "Warmth", "AURORA" to "Aurora", "SOFT_UI" to "Soft UI", "EXPRESSIVE" to "Expressive")"""

content = content.replace(old_settings, new_settings)

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
