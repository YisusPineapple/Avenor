#!/bin/bash

echo "🧹 Iniciando limpieza profunda del repositorio Avenor..."

# 1. Eliminar cachés de Python y artefactos de migración
echo "Borrando cachés de Python..."
find . -type d -name "__pycache__" -exec rm -rf {} + 2>/dev/null
find . -type d -name ".pytest_cache" -exec rm -rf {} + 2>/dev/null
find . -type f -name "*.pyc" -delete 2>/dev/null
find . -type f -name "*.pyo" -delete 2>/dev/null

# 2. Eliminar logs temporales
echo "Borrando logs temporales..."
find . -type f -name "*.log" -delete 2>/dev/null

# 3. Eliminar remanentes de Flutter/Dart si volvieron a aparecer
echo "Borrando remanentes de frameworks antiguos..."
rm -rf .dart_tool/ 2>/dev/null
rm -rf build_flutter/ 2>/dev/null

# 4. Limpiar cachés corruptas de Gradle local (opcional pero recomendado)
echo "Limpiando cachés de build locales..."
rm -rf .gradle/daemon/ 2>/dev/null
rm -rf app/build/ 2>/dev/null
rm -rf shared/build/ 2>/dev/null
rm -rf desktop/build/ 2>/dev/null
rm -rf .build-outputs/ 2>/dev/null

echo "✅ Limpieza completada con éxito. Tu entorno está puro y optimizado."
