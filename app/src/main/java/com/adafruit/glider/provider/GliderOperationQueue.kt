package com.adafruit.glider.provider

/**
 * Created by Antonio García (antonio@openroad.es)
 */

internal class GliderOperationQueue {
    private val mQueue = mutableListOf<GliderOperation>()

    fun add(command: GliderOperation) {
        var shouldExecute: Boolean
        synchronized(mQueue) {
            shouldExecute = mQueue.isEmpty()
            mQueue.add(command)
        }
        if (shouldExecute) {
            command.execute()
        }
    }

    fun first(): GliderOperation? {
        synchronized(mQueue) { return mQueue.firstOrNull() }
    }

    fun clear() {
        synchronized(mQueue) { mQueue.clear() }
    }

    fun executeNext() {
        var nextCommand: GliderOperation?
        synchronized(mQueue) {
            mQueue.removeFirstOrNull()
            nextCommand = mQueue.firstOrNull()
        }
        nextCommand?.execute()
    }
/*
    fun containsCommandType(type: Int): Boolean {
        synchronized(mQueue) {
            return mQueue.firstOrNull { it.type == type } != null
        }
    }*/
}