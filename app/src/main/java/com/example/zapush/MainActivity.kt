package com.example.zapush

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity(R.layout.activity_main) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createTestingFile()

        findViewById<View>(R.id.button).setOnClickListener {
            Zapush().execute(
                File(filesDir, "Bla.java"),
                "Bla",
                "fire",
                hashMapOf("context" to (applicationContext as Context))
            )
        }
    }

    private fun createTestingFile() {
        val file = File(filesDir, "Bla.java")
        if (file.exists()) {
            file.delete()
        }
        file.createNewFile()
        file.writeText(
            "package com.example.zapush;" +
                    "import android.content.Context;" +
                    "import android.widget.Toast;" +
                    "public class Bla {" +
                    "        String mText = \"hello member\";" +
                    "    public void fire(Context context){" +
                    "        String text = new String(\"hello\");" +
                    "        Toast.makeText(context,text,Toast.LENGTH_LONG).show();" +
                    "    }" +
                    "}"
        )
    }
}