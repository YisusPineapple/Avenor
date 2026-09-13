sed -i '/import kotlinx.coroutines.launch/d' app/src/main/java/io/github/yisus/nexo/AppDatabase.kt
sed -i '/import kotlinx.coroutines.GlobalScope/d' app/src/main/java/io/github/yisus/nexo/AppDatabase.kt
sed -i '/import kotlinx.coroutines.Dispatchers/d' app/src/main/java/io/github/yisus/nexo/AppDatabase.kt

sed -i '/import androidx.compose.ui.graphics.asImageBitmap/d' app/src/main/java/io/github/yisus/nexo/MainActivity.kt
sed -i '/import androidx.lifecycle.lifecycleScope/d' app/src/main/java/io/github/yisus/nexo/MainActivity.kt

sed -i 's/package io.github.yisus.nexo/package io.github.yisus.nexo\n\nimport kotlinx.coroutines.launch\nimport kotlinx.coroutines.GlobalScope\nimport kotlinx.coroutines.Dispatchers/' app/src/main/java/io/github/yisus/nexo/AppDatabase.kt
sed -i 's/package io.github.yisus.nexo/package io.github.yisus.nexo\n\nimport androidx.compose.ui.graphics.asImageBitmap\nimport androidx.lifecycle.lifecycleScope/' app/src/main/java/io/github/yisus/nexo/MainActivity.kt
