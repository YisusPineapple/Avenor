with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    main = f.read()

main = main.replace("onRename = { newName ->", "onRename = { newName: String ->")

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(main)
