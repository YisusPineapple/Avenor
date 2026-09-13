with open("app/build.gradle.kts", "r") as f:
    content = f.read()

content = content.replace(
    '// Coil\n    implementation("io.coil-kt:coil-compose:2.5.0")',
    '// Coil\n    implementation("io.coil-kt:coil-compose:2.5.0")\n    implementation("androidx.palette:palette-ktx:1.0.0")'
)

with open("app/build.gradle.kts", "w") as f:
    f.write(content)
