import re

# Fix CrossfadeManager
with open("app/src/main/java/io/github/yisus/nexo/CrossfadeManager.kt", "r") as f:
    crossfade = f.read()
crossfade = crossfade.replace("player.seekParameters = androidx.media3.common.SeekParameters.EXACT", "player.setSeekParameters(androidx.media3.common.SeekParameters.EXACT)")
crossfade = crossfade.replace("player.seekParameters = androidx.media3.common.SeekParameters.CLOSEST_SYNC", "player.setSeekParameters(androidx.media3.common.SeekParameters.CLOSEST_SYNC)")
with open("app/src/main/java/io/github/yisus/nexo/CrossfadeManager.kt", "w") as f:
    f.write(crossfade)

# Fix MainActivity structure
with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    main = f.read()

# I need to ensure RenameDialog and TrashRecoveryScreen are top-level composables.
# Let's extract them if they ended up nested.
# Or better, just rewrite them properly at the end of the file.

