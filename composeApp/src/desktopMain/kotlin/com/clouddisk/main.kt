package com.clouddisk

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "CloudDisk",
        state = rememberWindowState(width = 1000.dp, height = 720.dp),
    ) {
        App()
    }
}
