with open("build.gradle.kts", "r") as f:
    root_gradle = f.read()

if "org.jetbrains.kotlin.plugin.serialization" not in root_gradle:
    root_gradle = root_gradle.replace(
        'id("com.google.devtools.ksp") version "2.0.0-1.0.22" apply false',
        'id("com.google.devtools.ksp") version "2.0.0-1.0.22" apply false\n    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0" apply false'
    )
    with open("build.gradle.kts", "w") as f:
        f.write(root_gradle)

with open("app/build.gradle.kts", "r") as f:
    app_gradle = f.read()

if "org.jetbrains.kotlin.plugin.serialization" not in app_gradle:
    app_gradle = app_gradle.replace(
        'id("com.google.devtools.ksp")',
        'id("com.google.devtools.ksp")\n    id("org.jetbrains.kotlin.plugin.serialization")'
    )

if "kotlinx-serialization-json" not in app_gradle:
    app_gradle = app_gradle.replace(
        '// Media3',
        '// Serialization & WorkManager\n    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")\n    implementation("androidx.work:work-runtime-ktx:2.9.0")\n\n    // Media3'
    )

with open("app/build.gradle.kts", "w") as f:
    f.write(app_gradle)
