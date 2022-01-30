package com.example.zapush

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity(R.layout.activity_main) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Zapush().execute(
            File(filesDir, "Bla.java"),
            "Bla",
            "fire",
            hashMapOf("context" to (applicationContext as Context))
        )
    }
}