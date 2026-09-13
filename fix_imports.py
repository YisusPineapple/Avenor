import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

imports = """
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import androidx.compose.runtime.collectAsState
"""

if "import kotlinx.coroutines.flow.stateIn" not in content:
    content = content.replace("import kotlinx.coroutines.flow.StateFlow", "import kotlinx.coroutines.flow.StateFlow\n" + imports)

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
