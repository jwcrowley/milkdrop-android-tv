package com.example.milkdrop.ui

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.example.milkdrop.R

/**
 * Hosts [PresetBrowserFragment].
 *
 * Declared in the manifest with `android:exported="false"` and
 * `configChanges="orientation|screenSize"`.
 */
class PresetBrowserActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preset_browser)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.preset_browser_container, PresetBrowserFragment())
                .commitNow()
        }
    }
}
