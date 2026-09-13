with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

if "@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun MetadataEditorOverlay" not in content:
    content = content.replace("@Composable\nfun MetadataEditorOverlay", "@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n@Composable\nfun MetadataEditorOverlay")

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
