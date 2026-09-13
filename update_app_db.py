import re

with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "r") as f:
    content = f.read()

# Add onCreate to populate presets
on_create_logic = """
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
                    
                    override fun onOpen(db: SupportSQLiteDatabase) {
"""

content = content.replace("override fun onOpen(db: SupportSQLiteDatabase) {", on_create_logic)

with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "w") as f:
    f.write(content)
