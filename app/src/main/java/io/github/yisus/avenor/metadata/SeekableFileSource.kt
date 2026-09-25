package io.github.yisus.avenor.metadata

import java.io.Closeable

/**
 * Abstract seekable file source for metadata reading and format sniffing.
 *
 * Provides thread-safe, boundary-checked random access over heterogeneous storage backends
 * (direct filesystem, Android ParcelFileDescriptor/ContentResolver, memory buffers).
 *
 * Characteristics:
 * - Deterministic [seek] and [tell] operations.
 * - Boundary-checked [read] into byte arrays.
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
    fun readByte(): Int {
        val single = ByteArray(1)
        val readCount = read(single, 0, 1)
        return if (readCount <= 0) -1 else single[0].toInt() and 0xFF
    }

    /**
     * Creates an independent view/clone of this source at cursor 0 without duplicating underlying storage.
     */
    fun duplicate(): SeekableFileSource
}

/**
 * In-memory seekable source implementation primarily for unit testing and header sniffing.
 */
class MemorySeekableFileSource(
    private val data: ByteArray
) : SeekableFileSource {
    private var position: Long = 0L
    private var isClosed: Boolean = false

    override val size: Long get() = data.size.toLong()

    override fun tell(): Long {
        checkNotClosed()
        return position
    }

    override fun seek(position: Long) {
        checkNotClosed()
        require(position in 0..size) { "Seek position $position out of bounds [0..$size]" }
        this.position = position
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        checkNotClosed()
        if (length == 0) return 0
        if (position >= size) return -1
        val available = (size - position).toInt()
        val toRead = minOf(length, available)
        System.arraycopy(data, position.toInt(), buffer, offset, toRead)
        position += toRead
        return toRead
    }

    override fun duplicate(): SeekableFileSource {
        checkNotClosed()
        return MemorySeekableFileSource(data)
    }

    override fun close() {
        isClosed = true
    }

    private fun checkNotClosed() {
        check(!isClosed) { "SeekableFileSource is closed" }
    }
}
