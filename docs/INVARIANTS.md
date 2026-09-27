=== FILE: docs/INVARIANTS.md ===
# Invariantes de Avenor

Reglas que NUNCA deben romperse.

## Audio
- Orden cadena DSP: Downmix → ReplayGain → Equalizer → SafeLimiter.
- ReplayGain modo OFF → 0.0 dB estricto.
- SafeLimiter ceiling ≤ 1.0f en toda salida.
- Downmix usa LFE discard por ITU-R BS.775-3.
- EQ: RBJ biquad, DF2T, Nyquist guard a 0.42 * Fs.

## Base de datos
- exportSchema = true en Room.
- Prohibido fallbackToDestructiveMigration().
- Migraciones incrementales no destructivas.
- Checksum SHA-256 sobre payload canónico en backup.

## Privacidad
- Offline estricto: ninguna feature core usa red.
- Cero telemetría. Cero analytics SDK.
- Cero conexiones salientes por defecto.

## Promise-keeping
- isAvailableInBuild = true solo si .aar está enlazado Y testeado.
- Ninguna columna de config sin consumidor en el mismo PR.

## Compose / UI
- Sin Modifier.blur() en ECO. Sin excepciones.
- Una sombra por card máximo.
- Gradientes lineales o radiales cacheados.
- Sin allocations en hot path de audio.

## Testing
- Cada PR añade o actualiza al menos un test.
- Tests de migración Room obligatorios por versión nueva.
- 0 warnings de detekt en el diff.
=== END FILE ===