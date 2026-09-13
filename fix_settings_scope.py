with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

settings_fix = """fun SettingsScreen(viewModel: PlaybackViewModel) {
val appSettings by viewModel.appSettings.collectAsState()
val settings = appSettings ?: AppSetting()
val coroutineScope = rememberCoroutineScope()
val context = LocalContext.current"""

content = content.replace("fun SettingsScreen(viewModel: PlaybackViewModel) {\nval appSettings by viewModel.appSettings.collectAsState()\nval settings = appSettings ?: AppSetting()", settings_fix)

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
