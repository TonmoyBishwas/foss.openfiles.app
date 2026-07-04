package foss.openfiles.app.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import foss.openfiles.app.R
import foss.openfiles.app.ui.home.HomeFragment
import foss.openfiles.app.ui.permission.PermissionFragment

class MainActivity : AppCompatActivity() {

    private val legacyPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { showEntryScreen() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) showEntryScreen()
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the All files access settings screen.
        if (supportFragmentManager.findFragmentById(R.id.fragment_container)
            is PermissionFragment && hasStoragePermission()
        ) {
            showEntryScreen()
        }
    }

    fun hasStoragePermission(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }.onFailure {
                runCatching {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        } else {
            legacyPermission.launch(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun showEntryScreen() {
        val target: Fragment =
            if (hasStoragePermission()) HomeFragment() else PermissionFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, target)
            .commitAllowingStateLoss()
    }

    fun push(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in, R.anim.fade_out, R.anim.fade_in, R.anim.slide_out
            )
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val current = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (current is BackHandler && current.onBackPressed()) return
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    interface BackHandler {
        /** Return true if the back press was consumed. */
        fun onBackPressed(): Boolean
    }
}
