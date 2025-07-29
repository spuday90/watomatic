package com.parishod.watomatic.fragment

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.parishod.watomatic.R
import com.parishod.watomatic.activity.contactselector.ContactSelectorActivity
import com.parishod.watomatic.model.utils.ContactsHelper

class AdvancedSettingsFragment : PreferenceFragmentCompat() {
    private var contactsHelper: ContactsHelper? = null
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_advanced_settings, rootKey)
        contactsHelper = ContactsHelper.getInstance(context)
        val enableContactRepliesPreference =
            findPreference<SwitchPreference>(getString(R.string.pref_reply_contacts))
        enableContactRepliesPreference?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean && !contactsHelper!!.hasContactPermission() &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            ) {
                contactsHelper!!.requestContactPermission(activity)
            }
            true
        }
        val advancedPref = findPreference<Preference>(getString(R.string.key_pref_select_contacts))
        advancedPref?.setOnPreferenceClickListener {
            startActivity(Intent(activity, ContactSelectorActivity::class.java))
            true
        }
    }
}
