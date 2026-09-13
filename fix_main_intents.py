with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# I need to add onNewIntent and intent handling to MainActivity class.
intent_logic = """    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val action = intent?.action
        // Note: the ViewModel needs to be accessible here, or we use a static/shared flow.
        // Let's rely on standard ViewModel fetching since Compose holds it.
        // Actually, we can dispatch a broadcast or just rely on a Singleton/StateFlow for navigation requests.
    }"""

# A better way in Compose is to use DisposableEffect with Intent.
# We'll modify NexoAppRoot to listen to Intents.
effect = """
// Intent Handling
androidx.compose.runtime.DisposableEffect(activity) {
    val listener = androidx.core.util.Consumer<android.content.Intent> { intent ->
        when (intent.action) {
            NexoWidgetProvider.ACTION_OPEN_NOW_PLAYING -> viewModel.switchScreen(Screen.NowPlaying)
            NexoWidgetProvider.ACTION_OPEN_LIBRARY -> viewModel.switchScreen(Screen.Library)
        }
    }
    activity?.addOnNewIntentListener(listener)
    
    // Check initial intent
    when (activity?.intent?.action) {
        NexoWidgetProvider.ACTION_OPEN_NOW_PLAYING -> viewModel.switchScreen(Screen.NowPlaying)
        NexoWidgetProvider.ACTION_OPEN_LIBRARY -> viewModel.switchScreen(Screen.Library)
    }
    
    onDispose {
        activity?.removeOnNewIntentListener(listener)
    }
}
"""

if "activity?.addOnNewIntentListener" not in content:
    content = content.replace("val currentScreen by viewModel.currentScreen.collectAsState()", effect + "\nval currentScreen by viewModel.currentScreen.collectAsState()")

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
