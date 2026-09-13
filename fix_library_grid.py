import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

imports_grid = """
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
"""
if "import androidx.compose.foundation.lazy.grid.LazyVerticalGrid" not in content:
    content = content.replace("import androidx.compose.foundation.lazy.itemsIndexed", "import androidx.compose.foundation.lazy.itemsIndexed\n" + imports_grid)

# Replace LazyColumn with LazyVerticalGrid and items with grid items
old_lazycol = "LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)) {"
new_lazycol = """
            val windowClass = ResponsiveGridManager.getWindowSizeClass(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp)
            val columns = ResponsiveGridManager.getGridCells(windowClass)
            val paddingValues = ResponsiveGridManager.getPadding(windowClass)
            
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(columns),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = paddingValues.calculateTopPadding(), bottom = 80.dp)
            ) {
"""
content = content.replace(old_lazycol, new_lazycol)

# Replace item { with item(span = { GridItemSpan(maxLineSpan) }) {
content = content.replace("item {\n                    Text(\"Daily Mix For You\"", "item(span = { GridItemSpan(maxLineSpan) }) {\n                    Text(\"Daily Mix For You\"")
content = content.replace("item {\n                    Row(modifier = Modifier.fillMaxWidth()", "item(span = { GridItemSpan(maxLineSpan) }) {\n                    Row(modifier = Modifier.fillMaxWidth()")
content = content.replace("item {\n                    Text(\"Recently Played\"", "item(span = { GridItemSpan(maxLineSpan) }) {\n                    Text(\"Recently Played\"")
content = content.replace("item { Text(\"All Songs\"", "item(span = { GridItemSpan(maxLineSpan) }) { Text(\"All Songs\"")

# Replace items(songs.size) with items(songs.size) inside Grid
content = content.replace("items(songs.size) {\n", "items(songs.size) {\n")

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
