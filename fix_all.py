import re

for filename in ["app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "app/src/main/java/io/github/yisus/nexo/AppDatabase.kt"]:
    with open(filename, "r") as f:
        content = f.read()
    
    # Remove all weird spacing
    content = content.replace("package io.github.yisus.nexoimport", "package io.github.yisus.nexo\nimport")
    content = content.replace("asImageBitmapimport", "asImageBitmap\nimport")
    content = content.replace("lifecycleScopeimport", "lifecycleScope\nimport")
    content = content.replace("lifecycleScopepackage", "lifecycleScope\npackage")
    content = content.replace("launchimport", "launch\nimport")
    content = content.replace("GlobalScopeimport", "GlobalScope\nimport")
    content = content.replace("Dispatchersimport", "Dispatchers\nimport")
    content = content.replace("Dispatcherspackage", "Dispatchers\npackage")
    
    # Just in case, add \n before import
    content = re.sub(r'([a-zA-Z0-9_\*])import ', r'\1\nimport ', content)
    
    with open(filename, "w") as f:
        f.write(content)
