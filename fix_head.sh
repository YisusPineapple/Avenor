echo "package io.github.yisus.nexo" > temp.kt
echo "" >> temp.kt
echo "import androidx.compose.ui.graphics.asImageBitmap" >> temp.kt
echo "import androidx.lifecycle.lifecycleScope" >> temp.kt
tail -n +3 app/src/main/java/io/github/yisus/nexo/MainActivity.kt >> temp.kt
mv temp.kt app/src/main/java/io/github/yisus/nexo/MainActivity.kt
