import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

bad_pattern = """
                        modifier = Modifier.fillMaxWidth()
                            .drawWithContent {
                                graphicsLayer.record { this@drawWithContent.drawContent() }
                                drawLayer(graphicsLayer)
                            }
"""
good_pattern = """
                        modifier = Modifier.fillMaxWidth()
                            .androidx.compose.ui.draw.drawWithContent {
                                graphicsLayer.record(density = this, layoutDirection = layoutDirection, size = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())) {
                                    this@drawWithContent.drawContent()
                                }
                                drawLayer(graphicsLayer)
                            }
"""
content = content.replace(bad_pattern.strip(), good_pattern.strip())

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
