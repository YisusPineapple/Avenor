import re

with open("app/src/main/java/io/github/yisus/nexo/AudioRepository.kt", "r") as f:
    content = f.read()

old_projection = """
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )
"""

new_projection = """
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )
"""
content = content.replace(old_projection.strip(), new_projection.strip())

old_columns = """
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
"""
new_columns = """
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
"""
content = content.replace(old_columns.strip(), new_columns.strip())

old_add = """
                val albumArtUri = "content://media/external/audio/albumart/$albumId"
                
                songs.add(
                    Song(
                        id = id,
                        uri = contentUri.toString(),
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = duration,
                        albumArtUri = albumArtUri
                    )
                )
"""

new_add = """
                val albumArtUri = "content://media/external/audio/albumart/$albumId"
                val filePath = try { cursor.getString(dataColumn) } catch(e: Exception) { null }
                
                val specs = ExtendedMetadataExtractor.extract(context, contentUri, filePath)
                
                songs.add(
                    Song(
                        id = id,
                        uri = contentUri.toString(),
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = duration,
                        albumArtUri = albumArtUri,
                        bitDepth = specs.bitDepth,
                        sampleRate = specs.sampleRate,
                        mimeType = specs.mimeType,
                        fileExtension = specs.fileExtension
                    )
                )
"""
content = content.replace(old_add.strip(), new_add.strip())

with open("app/src/main/java/io/github/yisus/nexo/AudioRepository.kt", "w") as f:
    f.write(content)
