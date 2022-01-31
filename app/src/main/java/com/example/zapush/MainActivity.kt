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
            "package com.example.zapush;\n" +
                    "\n" +
                    "import android.content.Context;\n" +
                    "import android.widget.Toast;\n" +
                    "\n" +
                    "import java.io.File;\n" +
                    "import java.io.FileWriter;\n" +
                    "import java.io.IOException;\n" +
                    "import java.util.Scanner;\n" +
                    "\n" +
                    "public class Bla {\n" +
                    "    String mText = \"getting coins prices\";\n" +
                    "\n" +
                    "    public void fire(Context context) throws IOException {\n" +
                    "        String text = new String(mText);\n" +
                    "        \n" +
                    "        File fileCheck = new File(context.getFilesDir(),\"test_file.txt\");\n" +
                    "        fileCheck.createNewFile();\n" +
                    "        FileWriter fileWriter = new FileWriter(fileCheck);\n" +
                    "        fileWriter.write(\"from file\");\n" +
                    "        fileWriter.flush();\n" +
                    "        fileWriter.close();\n" +
                    "\n" +
                    "        Scanner myReader = new Scanner(fileCheck);\n" +
                    "        \n" +
                    "        Toast.makeText(context, myReader.nextLine(), Toast.LENGTH_LONG).show();\n" +
                    "    }\n" +
                    "}"
        )
    }
}