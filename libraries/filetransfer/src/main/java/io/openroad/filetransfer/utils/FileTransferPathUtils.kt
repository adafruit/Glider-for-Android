package io.openroad.filetransfer.utils

/**
 * Created by Antonio García (antonio@openroad.es)
 */

private const val fileTransferPathUtilsSeparatorCharacter = '/'
private const val fileTransferPathUtilsSeparator = "$fileTransferPathUtilsSeparatorCharacter"

class FileTransferPathUtils {
}

// region Path management

fun pathRemovingFilename(path: String): String  {
    val index = path.lastIndexOf(fileTransferPathUtilsSeparator)
    return if (index == -1) path else path.substring(0, index + 1)
}

fun filenameFromPath(path: String): String =
    path.substringAfterLast(fileTransferPathUtilsSeparator)


fun upPath(from: String): String {
    // Remove trailing separator if exists
    val filenamePath: String
    if (from.last() == fileTransferPathUtilsSeparatorCharacter) {
        filenamePath = from.dropLast(1)
    }
    else {
        filenamePath = from
    }

    // Remove any filename
    val pathWithoutFilename = pathRemovingFilename(filenamePath)
    return pathWithoutFilename
}

// endregion

// region Root Directory
fun isRootDirectory(path: String): Boolean {
    return path == fileTransferPathUtilsSeparator
}

// endregion
