with open("app/src/main/java/io/github/yisus/nexo/SystemOptimizations.kt", "r") as f:
    lines = f.readlines()

new_lines = []
imports_to_move = []

for line in lines:
    if line.startswith("import androidx.activity.compose.rememberLauncherForActivityResult") or \
       line.startswith("import androidx.activity.result.contract.ActivityResultContracts") or \
       line.startswith("import androidx.compose.ui.platform.LocalContext") or \
       line.startswith("import androidx.compose.runtime.rememberCoroutineScope") or \
       line.startswith("import kotlinx.coroutines.launch") or \
       line.startswith("import kotlinx.coroutines.Dispatchers") or \
       line.startswith("import kotlinx.coroutines.withContext"):
        imports_to_move.append(line)
    else:
        new_lines.append(line)

final_lines = []
for line in new_lines:
    final_lines.append(line)
    if line.startswith("package"):
        for imp in imports_to_move:
            final_lines.append(imp)

with open("app/src/main/java/io/github/yisus/nexo/SystemOptimizations.kt", "w") as f:
    f.writelines(final_lines)
