import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

match = re.search(r'@Composable\s*fun SongListItem.*?}', content, re.DOTALL)
if match:
    print(match.group(0))
    # we need to find the matching closing brace.
