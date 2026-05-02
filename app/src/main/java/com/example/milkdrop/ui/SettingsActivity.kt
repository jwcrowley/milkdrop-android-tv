package com.example.milkdrop.ui

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment
import com.example.milkdrop.R

/**
 * Hosts [SettingsFragment] (a [GuidedStepSupportFragment]).
 *
 * Declared in the manifest with `android:exported="false"` and
 * `configChanges="orientation|screenSize"`.
 */
class SettingsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            GuidedStepSupportFragment.addAsRoot(this, SettingsFragment(), android.R.id.content)
        }
    }
}
