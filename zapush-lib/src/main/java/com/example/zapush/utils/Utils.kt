package com.example.zapush.utils

object Utils {

    fun exceptionMessage(message:String): Exception {
        return Exception("Zapush: $message")
    }
}