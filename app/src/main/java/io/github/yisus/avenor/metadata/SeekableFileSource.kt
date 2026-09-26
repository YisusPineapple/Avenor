package io.github.yisus.avenor.metadata

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

/**
 * Abstract seekable file source for metadata reading and format sniffing.
 *
 * Provides thread-safe, boundary-checked random access over heterogeneous storage backends
 * (direct filesystem, Android ParcelFileDescriptor/ContentResolver, memory buffers).
 *
 * Characteristics:
 * - Deterministic [seek] and [tell] operations.
 * - Boundary-checked [read] into byte arrays.
 * - Zero-allocation [readByte] in concrete implementations.
 * - Lightweight duplication for concurrent/independent head reads.
 */
interface SeekableFileSource : Closeable {
    /** Total size of the underlying source in bytes. */
    val size: Long

    /** Current read cursor offset from the beginning of the source (0 .. [size]). */
    fun tell(): Long

    /** Sets the current read cursor position in bytes. Throws [IllegalArgumentException] if position < 0 or > size. */
    fun seek(position: Long)

    /**
     * Reads up to [length] bytes from the current cursor into [buffer] starting at [offset].
     * Returns the actual number of bytes read, or -1 if the end of the source is reached.
     */
    fun read(buffer: ByteArray, offset: Int, length: Int): Int

    /**
     * Reads a single byte, returning it as an Int in 0..255, or -1 on EOF.
     */
    fun readByte(): Int

    /**
     * Creates an independent view/clone of this source at cursor 0 without duplicating underlying storage.
     */
    fun duplicate(): SeekableFileSource

    companion object {
        private const val MAX_FALLBACK_HEADER_BYTES = 256 * 1024

        /**
         * Creates a [SeekableFileSource] backed by a physical [File].
         */
        fun fromFile(file: File): SeekableFileSource = RandomAccessFileSeekableSource(file)

        /**
         * Opens a [SeekableFileSource] from either a direct [filePath] (when accessible)
         * or via Android's [Context.getContentResolver] for a `content://` or `file://` [uri].
         */
        fun fromContextUri(context: Context, uri: Uri, filePath: String? = null): SeekableFileSource? {
            if (!filePath.isNullOrBlank()) {
                val file = File(filePath)
                if (file.exists() && file.isFile && file.canRead()) {
                    return try {
                        RandomAccessFileSeekableSource(file)
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            if (uri.scheme == "file") {
                val path = uri.path
                if (!path.isNullOrBlank()) {
                    val file = File(path)
                    if (file.exists() && file.isFile && file.canRead()) {
                        return try {
                            RandomAccessFileSeekableSource(file)
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }
            return try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null && pfd.statSize >= 0L) {
                    ParcelFileDescriptorSeekableSource(pfd, context, uri)
                } else {
                    pfd?.close()
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val buffer = ByteArray(MAX_FALLBACK_HEADER_BYTES)
                        var total = 0
                        while (total < buffer.size) {
                            val r = stream.read(buffer, total, buffer.size - total)
                            if (r <= 0) break
                            total += r
                        }
                        MemorySeekableFileSource(buffer.copyOf(total))
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}

/**
 * Thread-safe, zero-allocation in-memory seekable source implementation for unit testing
 * and buffered header sniffing.
 */
class MemorySeekableFileSource(
    private val data: ByteArray
) : SeekableFileSource {
    private val lock = Any()
    private var position: Long = 0L
    private var isClosed: Boolean = false

    override val size: Long get() = data.size.toLong()

    override fun tell(): Long = synchronized(lock) {
        checkNotClosedLocked()
        position
    }

    override fun seek(position: Long) = synchronized(lock) {
        checkNotClosedLocked()
        require(position in 0..size) { "Seek position $position out of bounds [0..$size]" }
        this.position = position
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = synchronized(lock) {
        checkNotClosedLocked()
        require(offset >= 0 && length >= 0 && offset + length <= buffer.size) {
            "Invalid buffer bounds: offset=$offset, length=$length, buffer.size=${buffer.size}"
        }
        if (length == 0) return 0
        if (position >= size) return -1
        val available = (size - position).toInt()
        val toRead = minOf(length, available)
        System.arraycopy(data, position.toInt(), buffer, offset, toRead)
        position += toRead
        toRead
    }

    override fun readByte(): Int = synchronized(lock) {
        checkNotClosedLocked()
        if (position >= size) return -1
        val b = data[position.toInt()].toInt() and 0xFF
        position++
        b
    }

    override fun duplicate(): SeekableFileSource = synchronized(lock) {
        checkNotClosedLocked()
        MemorySeekableFileSource(data)
    }

    override fun close() = synchronized(lock) {
        isClosed = true
    }

    private fun checkNotClosedLocked() {
        check(!isClosed) { "SeekableFileSource is closed" }
    }
}

/**
 * Thread-safe [SeekableFileSource] backed by a read-only [RandomAccessFile].
 */
class RandomAccessFileSeekableSource(
    private val file: File
) : SeekableFileSource {
    private val lock = Any()
    private val raf = RandomAccessFile(file, "r")
    private var isClosed = false
    override val size: Long = raf.length()

    override fun tell(): Long = synchronized(lock) {
        checkNotClosedLocked()
        raf.filePointer
    }

    override fun seek(position: Long) = synchronized(lock) {
        checkNotClosedLocked()
        require(position in 0..size) { "Seek position $position out of bounds [0..$size]" }
        raf.seek(position)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = synchronized(lock) {
        checkNotClosedLocked()
        require(offset >= 0 && length >= 0 && offset + length <= buffer.size) {
            "Invalid buffer bounds: offset=$offset, length=$length, buffer.size=${buffer.size}"
        }
        if (length == 0) return 0
        if (raf.filePointer >= size) return -1
        raf.read(buffer, offset, length)
    }

    override fun readByte(): Int = synchronized(lock) {
        checkNotClosedLocked()
        if (raf.filePointer >= size) return -1
        raf.read()
    }

    override fun duplicate(): SeekableFileSource = synchronized(lock) {
        checkNotClosedLocked()
        RandomAccessFileSeekableSource(file)
    }

    override fun close() = synchronized(lock) {
        if (!isClosed) {
            isClosed = true
            raf.close()
        }
    }

    private fun checkNotClosedLocked() {
        check(!isClosed) { "SeekableFileSource is closed" }
    }
}

/**
 * Thread-safe [SeekableFileSource] backed by an Android [ParcelFileDescriptor] FileChannel.
 */
class ParcelFileDescriptorSeekableSource(
    private val pfd: ParcelFileDescriptor,
    private val context: Context? = null,
    private val uri: Uri? = null
) : SeekableFileSource {
    private val lock = Any()
    private val fis = FileInputStream(pfd.fileDescriptor)
    private val channel = fis.channel
    private var isClosed = false
    private val singleByteBuffer = java.nio.ByteBuffer.allocate(1)

    override val size: Long = channel.size()

    override fun tell(): Long = synchronized(lock) {
        checkNotClosedLocked()
        channel.position()
    }

    override fun seek(position: Long): Unit = synchronized(lock) {
        checkNotClosedLocked()
        require(position in 0..size) { "Seek position $position out of bounds [0..$size]" }
        channel.position(position)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = synchronized(lock) {
        checkNotClosedLocked()
        require(offset >= 0 && length >= 0 && offset + length <= buffer.size) {
            "Invalid buffer bounds: offset=$offset, length=$length, buffer.size=${buffer.size}"
        }
        if (length == 0) return 0
        if (channel.position() >= size) return -1
        val bb = java.nio.ByteBuffer.wrap(buffer, offset, length)
        channel.read(bb)
    }

    override fun readByte(): Int = synchronized(lock) {
        checkNotClosedLocked()
        if (channel.position() >= size) return -1
        singleByteBuffer.clear()
        val r = channel.read(singleByteBuffer)
        if (r <= 0) -1 else singleByteBuffer.get(0).toInt() and 0xFF
    }

    override fun duplicate(): SeekableFileSource = synchronized(lock) {
        checkNotClosedLocked()
        if (context != null && uri != null) {
            val dupPfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (dupPfd != null) {
                return ParcelFileDescriptorSeekableSource(dupPfd, context, uri)
            }
        }
        val dupPfd = ParcelFileDescriptor.dup(pfd.fileDescriptor)
        ParcelFileDescriptorSeekableSource(dupPfd, context, uri)
    }

    override fun close() = synchronized(lock) {
        if (!isClosed) {
            isClosed = true
            try {
                channel.close()
            } catch (_: Exception) {}
            try {
                fis.close()
            } catch (_: Exception) {}
            try {
                pfd.close()
            } catch (_: Exception) {}
        }
    }

    private fun checkNotClosedLocked() {
        check(!isClosed) { "SeekableFileSource is closed" }
    }
}
