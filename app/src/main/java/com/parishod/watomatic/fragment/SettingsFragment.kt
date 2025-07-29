package com.parishod.watomatic.fragment

import android.os.Build
import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.parishod.watomatic.R
import com.parishod.watomatic.model.utils.AutoStartHelper
import com.parishod.watomatic.model.utils.ServieUtils

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_settings, rootKey)
        val showNotificationPref =
            findPreference<SwitchPreference>(getString(R.string.pref_show_notification_replied_msg))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && showNotificationPref != null) {
            showNotificationPref.title = getString(R.string.show_notification_label) + "(Beta)"
        }
        val autoStartPref = findPreference<Preference>(getString(R.string.pref_auto_start_permission))
        autoStartPref?.setOnPreferenceClickListener {
            checkAutoStartPermission()
            true
        }
        val foregroundServiceNotifPref =
            findPreference<SwitchPreference>(getString(R.string.pref_show_foreground_service_notification))
        if (foregroundServiceNotifPref != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                foregroundServiceNotifPref.isVisible = false
            }
            foregroundServiceNotifPref.setOnPreferenceChangeListener { _, newValue ->
                if (newValue == true) {
                    ServieUtils.getInstance(activity).startNotificationService()
                } else {
                    ServieUtils.getInstance(activity).stopNotificationService()
                }
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (activity != null) requireActivity().setTitle(R.string.settings)
    }

    private fun checkAutoStartPermission() {
        if (activity != null) {
            AutoStartHelper.getInstance().getAutoStartPermission(requireActivity())
        }
    }
}
