def clean(filename):
    with open(filename, "rb") as f:
        data = f.read()
    # decode ignoring errors
    text = data.decode("utf-8", "ignore")
    # strip weird characters
    text = "".join(c for c in text if c.isprintable() or c in "\n\r\t")
    
    # ensure package is first
    lines = [l.strip() for l in text.split("\n")]
    # remove all empty lines at start
    while lines and not lines[0]:
        lines.pop(0)
    
    with open(filename, "w") as f:
        f.write("\n".join(lines))

clean("app/src/main/java/io/github/yisus/nexo/MainActivity.kt")
clean("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt")
