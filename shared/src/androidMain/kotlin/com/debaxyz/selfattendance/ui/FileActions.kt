package com.debaxyz.selfattendance.ui

/** Platform file save/restore actions provided by the Android app entry point. */
class FileActions(
    val saveCsv: (Triple<String, String, String>) -> Unit,
    val saveJson: (Triple<String, String, String>) -> Unit,
    val restore: () -> Unit
)
