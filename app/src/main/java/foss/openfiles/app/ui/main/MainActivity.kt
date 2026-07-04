package foss.openfiles.app.ui.main

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Placeholder while the UI is under construction.
        setContentView(TextView(this).apply { text = "OpenFiles" })
    }
}
