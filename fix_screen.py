import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    main = f.read()

main = main.replace("object Settings : Screen()", "object Settings : Screen()\nobject TrashRecovery : Screen()")

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(main)
