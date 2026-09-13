import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

imports = """
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.AutoAwesome
"""

if "import androidx.compose.material.icons.filled.QueueMusic" not in content:
    content = content.replace("import androidx.compose.material.icons.filled.PlayCircle", "import androidx.compose.material.icons.filled.PlayCircle\n" + imports)

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
