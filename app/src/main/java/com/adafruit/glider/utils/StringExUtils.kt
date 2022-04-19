package com.adafruit.glider.utils

import java.util.*



/**
 * Replacement for Kotlin's deprecated `capitalize()` function.
 * from: https://stackoverflow.com/questions/67843986/is-there-a-shorter-replacement-for-kotlins-deprecated-string-capitalize-funct
 */
fun String.capitalized(): String {
    return this.replaceFirstChar {
        if (it.isLowerCase())
            it.titlecase(Locale.getDefault())
        else it.toString()
    }
}