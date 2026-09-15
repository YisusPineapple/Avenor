import re

with open('desktop/build.gradle.kts', 'r') as f:
    content = f.read()

replacement = """plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose") version "1.6.10"
}"""

content = content.replace('plugins {\n    kotlin("multiplatform")\n    id("org.jetbrains.compose") version "1.6.10"\n}', replacement)

with open('desktop/build.gradle.kts', 'w') as f:
    f.write(content)

with open('shared/build.gradle.kts', 'r') as f:
    shared_content = f.read()

shared_replacement = """plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose") version "1.6.10"
    id("com.android.library")
}"""

shared_content = shared_content.replace('plugins {\n    kotlin("multiplatform")\n    id("org.jetbrains.compose") version "1.6.10"\n    id("com.android.library")\n}', shared_replacement)

with open('shared/build.gradle.kts', 'w') as f:
    f.write(shared_content)

