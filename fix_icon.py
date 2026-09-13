import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

content = content.replace("Icons.AutoMirrored.Filled.List", "Icons.Default.QueueMusic")
# Also fix any residual formatMs formatting issues (Locale)
content = content.replace('String.format("%02d:%02d", minutes, seconds)', 'String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)')

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
