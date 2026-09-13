import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

if "import androidx.compose.foundation.lazy.grid.GridItemSpan" not in content:
    content = content.replace("import androidx.compose.foundation.lazy.grid.LazyVerticalGrid", "import androidx.compose.foundation.lazy.grid.LazyVerticalGrid\nimport androidx.compose.foundation.lazy.grid.GridItemSpan")

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
