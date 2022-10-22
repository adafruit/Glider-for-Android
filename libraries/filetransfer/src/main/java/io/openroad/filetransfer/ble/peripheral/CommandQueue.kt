package io.openroad.filetransfer.ble.peripheral

/**
 * Created by Antonio García (antonio@openroad.es)
 */

internal class CommandQueue {
    private val mQueue = mutableListOf<BleCommand>()

    fun add(command: BleCommand) {
        var shouldExecute: Boolean
        synchronized(mQueue) {
            shouldExecute = mQueue.isEmpty()
            mQueue.add(command)
        }
        if (shouldExecute) {
            command.execute()
        }
    }

    fun first(): BleCommand? {
        synchronized(mQueue) { return mQueue.firstOrNull() }
    }

    fun clear() {
        synchronized(mQueue) { mQueue.clear() }
    }

    fun executeNext() {
        var nextCommand: BleCommand?
        synchronized(mQueue) {
            mQueue.removeFirstOrNull()
            nextCommand = mQueue.firstOrNull()
        }
        nextCommand?.execute()
    }

    fun containsCommandType(type: Int): Boolean {
        synchronized(mQueue) {
            return mQueue.firstOrNull { it.type == type } != null
        }
    }
}