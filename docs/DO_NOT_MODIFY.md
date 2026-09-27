=== FILE: docs/DO_NOT_MODIFY.md ===
# Archivos DO-NOT-MODIFY sin aprobación humana explícita

Gemini NO debe modificar estos archivos salvo que la task activa del roadmap
lo mencione por nombre.

## DSP crítico (cadena de audio)
- app/src/main/java/io/github/yisus/avenor/dsp/UniversalDownmixAudioProcessor.kt
- app/src/main/java/io/github/yisus/avenor/dsp/EqualizerAudioProcessor.kt
- app/src/main/java/io/github/yisus/avenor/replaygain/ReplayGainAudioProcessor.kt
- app/src/main/java/io/github/yisus/avenor/replaygain/SafeLimiterAudioProcessor.kt
- app/src/main/java/io/github/yisus/avenor/audio/AvenorRenderersFactory.kt

## Servicio de reproducción
- app/src/main/java/io/github/yisus/avenor/PlaybackService.kt
  (excepto tasks 0.6 y posteriores que lo mencionen)

## Crossfade (hasta reimplementación)
- app/src/main/java/io/github/yisus/avenor/DualPlayerCrossfadeManager.kt
  (excepto task 0.1)

## Backup / migraciones
- app/src/main/java/io/github/yisus/avenor/BackupManager.kt
- app/src/main/java/io/github/yisus/avenor/AppDatabase.kt
  (excepto migraciones aprobadas explícitamente)

## Manifests y build
- app/build.gradle.kts
- build.gradle.kts
- settings.gradle.kts
- gradle.properties
- app/src/main/AndroidManifest.xml

## Reglas
- Si Gemini cree que necesita tocar uno de estos, debe PARAR y explicar por qué.
- El orquestador decide si aprueba o reformula la tarea.
=== END FILE ===