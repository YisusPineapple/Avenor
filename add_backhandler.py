import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

backhandler = """
                    Scaffold(
                        topBar = {
"""

backhandler_new = """
                    androidx.activity.compose.BackHandler(enabled = currentScreen != Screen.Library) {
                        currentScreen = Screen.Library
                    }
                    Scaffold(
                        topBar = {
"""

if "androidx.activity.compose.BackHandler(" not in content:
    content = content.replace(backhandler.strip(), backhandler_new.strip())

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
