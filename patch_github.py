import re
with open('.github/workflows/android-ci.yml', 'r') as f:
    content = f.read()

replacement = """jobs:
  build-android:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: ./
    steps:"""

content = content.replace("jobs:\n  build-android:\n    runs-on: ubuntu-latest\n    steps:", replacement)

replacement2 = """  build-release:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: ./
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    steps:"""

content = content.replace("  build-release:\n    runs-on: ubuntu-latest\n    if: github.event_name == 'push' && github.ref == 'refs/heads/main'\n    steps:", replacement2)

with open('.github/workflows/android-ci.yml', 'w') as f:
    f.write(content)
