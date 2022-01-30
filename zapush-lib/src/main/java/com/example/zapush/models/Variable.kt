package com.example.zapush.models

data class Variable(
    var name: String,
    var instance: Any?,
    var className: Class<*>
)