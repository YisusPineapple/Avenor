=== FILE: docs/CONVENTIONS.md ===
# Convenciones del proyecto

## Código Kotlin
- Style: official (Kotlin Coding Conventions).
- Max line length: 120.
- No wildcard imports.
- Sin runBlocking en producción.
- Sin GlobalScope.

## Compose
- Un composable por archivo cuando es pantalla.
- Componentes reusables en ui/components/.
- Modifier como último parámetro.
- @Composable sin side effects fuera de LaunchedEffect.

## Room
- Entidades en AppDatabase.kt o data/model/.
- DAOs con @Query explícito.
- Índices declarados en la entidad.
- Migraciones en el mismo archivo que las define.

## Tests
- Nombre: test<Action>_<Condition>_<Expected>.
- Robolectric para Android-dependent.
- Pure JVM para lógica pura (DSP, parsers).
- Un test por bug arreglado.

## Commits
- Formato: [FASE.TASK] descripción.
- Un commit por task.
- Sin WIP en main.
- Mensaje en español.

## Docs
- Todos los .md en docs/.
- Roadmap, invariantes, ledger siempre actualizados.
=== END FILE ===