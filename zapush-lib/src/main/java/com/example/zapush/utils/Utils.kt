package com.example.zapush.utils

import com.example.zapush.models.Variable

object Utils {

    fun exceptionMessage(message:String): Exception {
        return Exception("Zapush: $message")
    }
}