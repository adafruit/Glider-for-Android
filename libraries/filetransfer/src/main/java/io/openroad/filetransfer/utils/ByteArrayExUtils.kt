package io.openroad.filetransfer.utils

/**
 * Created by Antonio García (antonio@openroad.es)
 */


// region Hex conversions
fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

fun Byte.toHexString() = "%02x".format(this)
// endregion


// region littleEndian conversions
fun ByteArray.readLong64(offset: Int) = readLong(offset, 8)
fun ByteArray.readInt32(offset: Int) = readInt(offset, 4)
fun ByteArray.readInt16(offset: Int) = readInt(offset, 2)

private fun ByteArray.readInt(offset: Int, size: Int): Int {
    var result = 0
    var shift = 0
    for (i in offset until offset + size) {
        val value = this[i].toUByte().toInt()
        result = result or (value shl 8 * shift)      // shift left and 'or'
        shift += 1
    }
    return result
}

private fun ByteArray.readLong(offset: Int, size: Int): Long {
    var result: Long = 0
    var shift = 0
    for (i in offset until offset + size) {
        val value = this[i].toUByte().toLong()
        result = result or (value shl 8 * shift)      // shift left and 'or'
        shift += 1
    }
    return result
}

fun Int.toByteArray16bit() : ByteArray {
    val v0 = ((this ushr 0) and 0xFF).toByte()
    val v1 = ((this ushr 8) and 0xFF).toByte()
    return byteArrayOf(v0, v1)
}

fun Int.toByteArray32bit(): ByteArray {
    val v0 = ((this ushr 0) and 0xFF).toByte()
    val v1 = ((this ushr 8) and 0xFF).toByte()
    val v2 = ((this ushr 16) and 0xFF).toByte()
    val v3 = ((this ushr 24) and 0xFF).toByte()
    return byteArrayOf(v0, v1, v2, v3)
}

fun Long.toByteArray64bit(): ByteArray {
    val v0 = ((this ushr 0) and 0xFF).toByte()
    val v1 = ((this ushr 8) and 0xFF).toByte()
    val v2 = ((this ushr 16) and 0xFF).toByte()
    val v3 = ((this ushr 24) and 0xFF).toByte()

    val v4 = ((this ushr 32) and 0xFF).toByte()
    val v5 = ((this ushr 40) and 0xFF).toByte()
    val v6 = ((this ushr 48) and 0xFF).toByte()
    val v7 = ((this ushr 56) and 0xFF).toByte()

    return byteArrayOf(v0, v1, v2, v3, v4, v5, v6, v7)
}



// endregion