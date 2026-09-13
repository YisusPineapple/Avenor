import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Add MemoryWatchdog launch
old_launch = """LaunchedEffect(Unit) {
viewModel.initController(context)
val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
permissionLauncher.launch(permission)
}"""

new_launch = """LaunchedEffect(Unit) {
viewModel.initController(context)
val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
permissionLauncher.launch(permission)
launch { startMemoryWatchdog(context) }
}"""

content = content.replace(old_launch, new_launch)

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
