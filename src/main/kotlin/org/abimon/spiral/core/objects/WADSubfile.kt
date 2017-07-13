package org.abimon.spiral.core.objects

import org.abimon.spiral.util.OffsetInputStream
import org.abimon.visi.io.DataSource
import org.abimon.visi.io.readPartialBytes
import java.io.InputStream

data class WADSubdirectoryEntry(val name: String, val subfiles: List<WADSubfileEntry>)
data class WADSubfileEntry(val name: String, val isFile: Boolean)
data class WADFileEntry(val name: String, val fileSize: Long, val offset: Long, val wad: WAD) : DataSource {
    override val location: String = "WAD File ${wad.dataSource.location}, offset ${wad.dataOffset + offset} bytes"

    override val data: ByteArray
        get() = use { it.readPartialBytes(size.toInt()) }

    override val inputStream: InputStream
        get() = OffsetInputStream(wad.dataSource.inputStream, wad.dataOffset + offset, fileSize)

    override val size: Long = fileSize
}