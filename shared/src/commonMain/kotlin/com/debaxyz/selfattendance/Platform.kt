package com.debaxyz.selfattendance

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform