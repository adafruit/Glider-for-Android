package io.openroad.filetransfer.ble.scanner

/**
 * Created by Antonio García (antonio@openroad.es) on 13/3/22.
 */

import android.bluetooth.le.ScanRecord

fun ScanRecord.isManufacturerAdafruit(): Boolean {
    val kManufacturerAdafruitIdentifier = 2082
    return this.getManufacturerSpecificData(kManufacturerAdafruitIdentifier) != null
}



