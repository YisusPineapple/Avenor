import re

with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "r") as f:
    content = f.read()

old_oncreate = """
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val dao = INSTANCE?.musicDao()
                            if (dao != null) {
                                EqPresetValidator.predefinedPresets.forEach { preset ->
                                    dao.insertEqPreset(preset)
                                }
                            }
                        }
                    }
"""

new_oncreate = """
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val dao = INSTANCE?.musicDao()
                            if (dao != null) {
                                EqPresetValidator.predefinedPresets.forEach { preset ->
                                    dao.insertEqPreset(preset)
                                }
                                
                                // Apply benchmark tier on first launch
                                val tier = PerformanceBenchmark.evaluateDeviceTier(context)
                                dao.saveSettings(AppSetting(performanceMode = tier))
                            }
                        }
                    }
"""

content = content.replace(old_oncreate.strip(), new_oncreate.strip())

with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "w") as f:
    f.write(content)

