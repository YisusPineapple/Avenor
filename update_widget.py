with open("app/src/main/java/io/github/yisus/nexo/NexoWidgetProvider.kt", "r") as f:
    content = f.read()

# Update onReceive logic to send commands to PlaybackService
new_receive = """    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == ACTION_PLAY_PAUSE || action == ACTION_NEXT || action == ACTION_PREV) {
            val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                this.action = action
            }
            context.startForegroundService(serviceIntent)
        }
    }"""
    
import re
content = re.sub(r'override fun onReceive.*?    }', new_receive, content, flags=re.DOTALL)

with open("app/src/main/java/io/github/yisus/nexo/NexoWidgetProvider.kt", "w") as f:
    f.write(content)
