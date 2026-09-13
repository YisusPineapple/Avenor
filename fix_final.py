with open("app/src/main/java/io/github/yisus/nexo/CrossfadeManager.kt", "r") as f:
    crossfade = f.read()

crossfade = crossfade.replace("androidx.media3.common.SeekParameters", "androidx.media3.exoplayer.SeekParameters")

with open("app/src/main/java/io/github/yisus/nexo/CrossfadeManager.kt", "w") as f:
    f.write(crossfade)

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    main = f.read()
    
main = main.replace("Icons.AutoMirrored.Filled.ArrowBack", "Icons.Default.ArrowBack")

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(main)

