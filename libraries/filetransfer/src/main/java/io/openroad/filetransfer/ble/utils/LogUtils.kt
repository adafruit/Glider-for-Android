package com.adafruit.glider.utils

import java.util.logging.Logger

// https://stackoverflow.com/questions/34416869/idiomatic-way-of-logging-in-kotlin

// unwrap companion class to enclosing class given a Java Class
fun <T : Any> unwrapCompanionClass(ofClass: Class<T>): Class<*> {
    return ofClass.enclosingClass?.takeIf {
        it.kotlin.objectInstance?.javaClass == ofClass
    } ?: ofClass
}

fun <R : Any> R.LogUtils(): Lazy<Logger> {
    return lazy { Logger.getLogger(unwrapCompanionClass(this.javaClass).name) }
}
