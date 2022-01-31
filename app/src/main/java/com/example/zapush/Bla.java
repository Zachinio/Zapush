package com.example.zapush;

import android.content.Context;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

public class Bla {
    String mText = "getting coins prices";

    public void fire(Context context) throws IOException {
        String text = new String(mText);

        File fileCheck = new File(context.getFilesDir(),"test file");
        fileCheck.createNewFile();
        FileWriter fileWriter = new FileWriter(fileCheck);
        fileWriter.write("from file");

        Scanner myReader = new Scanner(fileCheck);

        Toast.makeText(context, myReader.nextLine(), Toast.LENGTH_LONG).show();
    }
}