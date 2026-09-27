=== FILE: docs/WORKFLOW.md ===
# Workflow de orquestación multi-IA

## Roles

- Humano: aprueba decisiones ❌, valida fases, controla scope.
- Orquestador (DeepSeek): descompone, formula prompts, valida outputs,
  mantiene ledger.
- Ejecutor (Gemini en AI Studio): modifica archivos, corre tests, push a GitHub.

## Loop por task

1. Humano abre chat con DeepSeek.
2. DeepSeek propone próximo chunk (task ID + archivo + criterio).
3. Humano aprueba o pide ajustes.
4. DeepSeek entrega prompt listo para Gemini.
5. Humano abre Google AI Studio → Build.
6. Humano pega el prompt.
7. Gemini propone plan (no aplica).
8. Humano revisa plan y autoriza.
9. Gemini modifica, corre tests, commit.
10. Humano copia diff + tests a DeepSeek.
11. DeepSeek valida contra criterio.
12. Si pasa → ledger = done.
13. Si falla → reformular, iterar.
14. Humano hace merge en GitHub si OK.

## Convención de commits

Formato: [FASE.TASK] descripción corta

Ejemplos:
- [0.1] desactivar crossfade automático en Settings
- [0.3] filtrar cola activa de guardadas
- [1.5] UI ReplayGain con switch y preamp

## Gestión de cuota Gemini

- Free tier: prompts limitados por día.
- Chunks atómicos → menos tokens → más tareas por día.
- No adjuntar repomix completo a Gemini. Solo archivo objetivo.
- Si se agota cuota, ledger permite retomar al día siguiente.

## Rollback

- Si Gemini rompe tests, usar Undo en AI Studio.
- Si ya hizo push, git revert <hash> desde GitHub.
- Nunca parchear a mano. Reformular prompt con más contexto.

## Anti-desastre

1. Nunca trabajar en main. Branch por fase.
2. Nunca permitir tocar archivo fuera de scope.
3. Nunca saltar modo plan.
4. Nunca commitear con tests rojos.
5. Nunca avanzar de fase sin cerrar anterior.
6. Nunca aprobar task ❌ sin decisión humana.
=== END FILE ===