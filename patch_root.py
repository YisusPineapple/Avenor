import re
with open('build.gradle.kts', 'r') as f:
    content = f.read()
if 'org.jetbrains.compose' not in content:
    content = content.replace('plugins {', 'plugins {\n    id("org.jetbrains.compose") version "1.6.10" apply false\n    id("org.jetbrains.kotlin.multiplatform") version "2.0.0" apply false')
with open('build.gradle.kts', 'w') as f:
    f.write(content)
