package com.adafruit.glider.utils

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/*
    From: https://stackoverflow.com/questions/66546962/jetpack-compose-how-do-i-refresh-a-screen-when-app-returns-to-foreground
    Usage:

    @Composable
    fun SomeComposable() {
       val lifeCycleState = LocalLifecycleOwner.current?.lifecycle?.observeAsSate()
       val state = lifeCycleState?.value
      // will re-render someComposable each time lifeCycleState will change
    }
*/
@Composable
fun Lifecycle.observeAsState(): State<Lifecycle.Event> {
    val state = remember { mutableStateOf(Lifecycle.Event.ON_ANY) }
    DisposableEffect(this) {
        val observer = LifecycleEventObserver { _, event ->
            state.value = event
        }
        this@observeAsState.addObserver(observer)
        onDispose {
            this@observeAsState.removeObserver(observer)
        }
    }
    return state
}

