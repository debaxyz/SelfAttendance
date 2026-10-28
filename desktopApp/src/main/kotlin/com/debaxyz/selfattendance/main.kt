package com.debaxyz.selfattendance

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "SelfAttendance",
    ) {
        App()
    }
}