
# Zapush
A Kotlin Library to execute Java code at runtime

Due to Google Play policies, updating android apps code is a violation, however it's only a violation if the app downloads an executable code.
Therefore, Zapush created to execute plain text Java code. This mechanism uses reflection techniques to run the dynamic Java code at runtime.

## Usage
Create a Zapush object and pass:
```kotlin
 Zapush().execute(
                File(filesDir, "Bla.java"),                               /* a file contains the java code */
                "Bla",                                                    /* the class name */
                "fire",                                                   /* the method name to run */
                hashMapOf("context" to (applicationContext as Context))   /* set of global parameters that can be used in the executed code */
            )
```

## Example
Let's say I want to run this Java code:

<details><summary>Bla.java</summary>
<p>

```java
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
```
 </p>
</details>



