=== FILE: docs/ROADMAP_V4.md ===
# Avenor Roadmap v4

Documento operativo. Cada task tiene ID, archivos, criterio, riesgo y aptitud
para IA (✅ / ⚠️ / ❌).

- ✅ = delegable a Gemini sin revisión previa
- ⚠️ = requiere aprobación del orquestador
- ❌ = decisión humana obligatoria

## FASE 0 — Bloqueantes

### 0.1. Desactivar crossfade automático (Android) ✅
Archivos: DualPlayerCrossfadeManager.kt, SettingsScreen.kt, PlayerViewModel.kt
Enfoque: eliminar switch autoMixEnabled de Settings. Mantener crossfadeEnabled
(manual skip). Columna Room queda huérfana hasta 0.2.
Aceptación: auto-transition no altera estado; manual skip funciona.

### 0.2. Guardas anti-borrado masivo ⚠️
Archivos: AppDatabase.kt (migración v20→v21), LibraryScanner.kt,
DatabaseRepository.kt.
Enfoque: añadir Song.availability Int (0=AVAILABLE, 1=MISSING, 2=EXCLUDED).
Verificar Environment.getExternalStorageState(). Si MediaStore vacío con Room
poblado → MISSING, no borrar. Borrar solo si MISSING en dos escaneos con
MediaStore sano.
Aceptación: test MediaStore vacío → MISSING. Test doble escaneo → borra.

### 0.3. Filtrar cola activa de guardadas ✅
Archivos: QueueScreen.kt, PlayerViewModel.kt.
Enfoque: filter { it.id != PlaybackCoordinator.ACTIVE_QUEUE_ID }.

### 0.4. Persistir letras embebidas en Room ⚠️
Archivos: AppDatabase.kt (v21→v22), LibraryScanner.kt, LyricsRepository.kt.
Enfoque: columnas lyricsSyncedLrc, lyricsUnsynced. Repo consulta Room primero,
luego .lrc hermano, luego embedded.
Aceptación: MP3 con USLT muestra letra sin .lrc.

### 0.5. POST_NOTIFICATIONS (Android 13+) ✅
Archivos: AndroidManifest.xml, MainActivity.kt, AvenorAppRoot.kt.

### 0.6. Auditoría foreground service API 34+ ✅
Archivos: PlaybackService.kt.
Aceptación: test Robolectric @Config(sdk=[34]) sin crash.

### 0.7. Decisión FFmpeg AAR ❌
Integrar .aar real O revertir isAvailableInBuild=false.

### 0.8. Detección VLC ausente (Desktop) ✅
Archivos: DesktopPlaybackController.kt, Main.kt.
Enfoque: try/catch init. Pantalla "VLC no encontrado" con link si falla.

### 0.9. CI para Desktop ✅
Archivos: .github/workflows/build-desktop.yml.
Enfoque: matriz [ubuntu-latest, windows-latest]. packageDeb/packageMsi.

### 0.10. Tests CreditSplitter y bitPerfectPassthrough ✅
Aceptación: "Simon & Garfunkel" no se divide; "AC/DC" no; feat sí.

## FASE 1 — Paridad mínima Android

1.1  Room v22→v23 metadatos extendidos ⚠️
     (ISRC, label, copyright, releaseType, explicit, barcode, rating,
      availability, mood, bpm, initialKey)
1.2  Multi-artistas y multi-géneros normalizados ⚠️
     Tablas artists, song_artists, genres, song_genres, composers,
     song_composers. Whitelist: Simon & Garfunkel, Earth Wind & Fire, AC/DC.
1.3  Parser MP4/M4A átomos ✅
1.4  Parser Xing/VBRI ✅
1.5  UI ReplayGain ✅
1.6  UI Folder Exclusion ✅
1.7  UI Backup/Restore ✅
1.8  Mini-player persistente ✅
1.9  Vistas multi-categoría (Albums, Artists, Genres, Years, Folders) ⚠️
1.10 Playlist rename/delete/reorder ✅
1.11 Cola drag&drop, rename/delete, save-as-playlist ✅
1.12 File watcher ContentObserver ⚠️
1.13 i18n español desde día uno ⚠️
1.14 Crash logger offline ✅
1.15 Unificar paquetes UI ✅
1.16 VACUUM a WorkManager ✅

## FASE 2 — Desktop serio

2.1  Decisión persistencia desktop ❌
2.2  MPRIS2 D-Bus (Linux) ⚠️
2.3  SMTC (Windows) ⚠️
2.4  Tray icon ✅
2.5  Global hotkeys ✅
2.6  Mini-player flotante ✅
2.7  Paridad pantallas desktop ⚠️
2.8  Asociación archivos + CLI args ✅
2.9  Notificaciones nativas ✅
2.10 Decisión backend audio desktop ❌
2.11 Decisión firma Windows ❌
2.12 Rpm + AppImage ⚠️

## FASE 3 — Competente de verdad

3.1  FFmpeg AAR integrado ⚠️
3.2  CUE sheets ⚠️
3.3  Editor de tags write-back ⚠️
3.4  Editor de letras + stamping ✅
3.5  EQ paramétrico + 10 bandas ⚠️
3.6  Presets por dispositivo ✅
3.7  Bass Boost, Stereo Widening, Pitch/Speed, Channel Tools, DRC ⚠️
3.8  Smart playlists ⚠️
3.9  Import/Export M3U/PLS/XSPF ✅
3.10 Widget dinámico Glance ⚠️
3.11 Notificación Palette + Seekbar ✅
3.12 Bluetooth avanzado ⚠️
3.13 Gestos Android ⚠️
3.14 AMOLED + color dinámico ✅
3.15 Recomendaciones Fase 2 ⚠️
3.16 Analytics ampliado ✅
3.17 Crossfade configurable con curvas ✅
3.18 AutoMix inteligente ⚠️

## FASE 4 — Audiophile y distribución

4.1  DSD nativo o DoP ⚠️
4.2  EBU R128 / LUFS ⚠️
4.3  Visualizadores (FFT, osciloscopio, VU real) ⚠️
4.4  Wear OS ⚠️
4.5  MPC ⚠️
4.6  Update checker (opt-in) ✅
4.7  Checklist F-Droid ✅
4.8  Sync Android ↔ Desktop ❌

## UI/UX — "Analog Editorial"

UI-1 (Fase 1) Tema base ✅
  Paleta Analog Warmth + Studio Dark + Editorial Light.
  Tipografía: serif (Playfair/Fraunces) + sans (Inter) + mono (JetBrains Mono).

UI-2 (Fase 1) Componentes signature ✅
  AnalogVUMeter (3 modos por tier), SpecPlate, EditorialHeader, BentoCard,
  SpectrumBar.

UI-3 (Fase 2) Dials y gestures ⚠️
UI-4 (Fase 2) Fondos procedurales cacheados ⚠️
UI-5 (Fase 3) Modo PRO completo ✅
UI-6 (Fase 3) Temas adicionales ✅

## Reglas de ejecución

1. No avanzar de fase sin cerrar la anterior.
2. No saltar Fase 0 → Fase 1 sin resolver 0.2.
3. Cada task en chunks atómicos de 1-2 archivos.
4. Cada chunk lleva test. Si falla, rollback.
5. Las ❌ requieren decisión humana antes de tocar código.
=== END FILE ===