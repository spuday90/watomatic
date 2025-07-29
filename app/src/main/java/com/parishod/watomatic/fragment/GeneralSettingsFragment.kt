package com.parishod.watomatic.fragment

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.parishod.watomatic.R
import com.parishod.watomatic.activity.main.MainActivity
import com.parishod.watomatic.model.preferences.PreferencesManager
import com.parishod.watomatic.model.utils.OpenAIHelper
import com.parishod.watomatic.network.model.openai.ModelData

class GeneralSettingsFragment : PreferenceFragmentCompat() {
    private var openAIModelPreference: ListPreference? = null
    private var openAIErrorDisplayPreference: Preference? = null // Added field
    private var preferencesManager: PreferencesManager? = null // Made into a field
    private var openApiSourcePreference: ListPreference? = null
    private var customApiUrlPreference: EditTextPreference? = null
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_general_settings, rootKey)
        preferencesManager = PreferencesManager.getPreferencesInstance(requireActivity()) // Initialize field
        val languagePref = findPreference<ListPreference>(getString(R.string.key_pref_app_language))
        languagePref?.setOnPreferenceChangeListener { _, newValue ->
            val thisLangStr =
                PreferencesManager.getPreferencesInstance(requireActivity()).getSelectedLanguageStr(null)
            if (thisLangStr == null || thisLangStr != newValue) {
                //switch app language here
                //Should restart the app for language change to take into account
                restartApp()
            }
            true
        }

        // PreferencesManager preferencesManager = PreferencesManager.getPreferencesInstance(requireActivity()); // Now a field
        openApiSourcePreference = findPreference("pref_openai_api_source")
        customApiUrlPreference = findPreference("pref_openai_custom_api_url")
        openApiSourcePreference?.setOnPreferenceChangeListener { _, newValue ->
            val source = newValue as String
            preferencesManager!!.saveOpenApiSource(source)
            updateCustomApiUrlVisibility(source)
            OpenAIHelper.invalidateCache()
            loadOpenAIModels()
            true
        }
        customApiUrlPreference?.setOnPreferenceChangeListener { _, newValue ->
            preferencesManager!!.saveCustomOpenAIApiUrl(newValue as String)
            OpenAIHelper.invalidateCache()
            loadOpenAIModels()
            true
        }
        updateCustomApiUrlVisibility(preferencesManager!!.openApiSource)
        val openAIApiKeyPreference = findPreference<EditTextPreference>("pref_openai_api_key")
        if (openAIApiKeyPreference != null) {
            openAIApiKeyPreference.summaryProvider = null // Disable provider before custom summary
            updateOpenAIApiKeySummary(openAIApiKeyPreference, preferencesManager!!.openAIApiKey)
            openAIApiKeyPreference.setOnPreferenceChangeListener { preference, newValue ->
                val newApiKey = newValue as String
                preferencesManager!!.saveOpenAIApiKey(newApiKey) // Save to EncryptedSharedPreferences
                updateOpenAIApiKeySummary(preference as EditTextPreference, newApiKey)
                OpenAIHelper.invalidateCache() // Invalidate model cache if API key changes
                if (openAIModelPreference != null) { // Ensure preference is found
                    openAIModelPreference!!.summary = getString(R.string.pref_openai_model_loading)
                    openAIModelPreference!!.isEnabled = false
                }
                loadOpenAIModels() // Reload models with new key

                // Clear any previous persistent error when API key changes
                preferencesManager!!.clearOpenAILastPersistentError()
                updateOpenAIErrorDisplay()
                true // True to update the state/summary of the preference
            }
        }
        val enableOpenAIPreference = findPreference<SwitchPreferenceCompat>("pref_enable_openai_replies")
        if (enableOpenAIPreference != null) {
            enableOpenAIPreference.summaryProvider = null // Disable provider before custom summary
            enableOpenAIPreference.isChecked = preferencesManager!!.isOpenAIRepliesEnabled
            enableOpenAIPreference.setOnPreferenceChangeListener { _, newValue ->
                preferencesManager!!.setEnableOpenAIReplies(newValue as Boolean)
                // Reload models when the main toggle changes
                if (openAIModelPreference != null) {
                    openAIModelPreference!!.summary = getString(R.string.pref_openai_model_loading)
                    openAIModelPreference!!.isEnabled = false
                }
                loadOpenAIModels()
                true // True to update the state of the preference
            }
        }
        openAIModelPreference = findPreference("pref_openai_model")
        if (openAIModelPreference != null) {
            openAIModelPreference!!.summaryProvider = null // Disable provider before custom summary
            openAIModelPreference!!.summary = getString(R.string.pref_openai_model_loading)
            openAIModelPreference!!.isEnabled = false
            openAIModelPreference!!.setOnPreferenceChangeListener { _, newValue ->
                val modelId = newValue as String
                // preferencesManager.saveSelectedOpenAIModel(modelId); // Need to add this method to PrefManager
                // For now, let's assume the ListPreference saves it to default SharedPreferences
                // and we'll handle saving to a specific key in PrefManager if needed later.
                // However, it's better to be explicit:
                // This line will be uncommented once saveSelectedOpenAIModel is added to PreferencesManager
                // preferencesManager.saveSelectedOpenAIModel(modelId);
                // The value is saved by default by ListPreference to its key "pref_openai_model"
                // in the default SharedPreferences. We need to read from there or save explicitly.
                // For now, let it save to default, and NotificationService will read from default.
                // Or, ensure PreferencesManager has a method for this specific key.
                // Let's add a placeholder for now to save it via PreferencesManager eventually.
                // This should be:
                preferencesManager!!.saveSelectedOpenAIModel(modelId) // Using dedicated method

                // Clear any previous persistent error when model selection changes successfully
                preferencesManager!!.clearOpenAILastPersistentError()
                updateOpenAIErrorDisplay()
                true
            }
            loadOpenAIModels()
        }
        openAIErrorDisplayPreference = findPreference("pref_openai_persistent_error_display")
        openAIErrorDisplayPreference?.setOnPreferenceClickListener {
            val lastError = preferencesManager!!.openAILastPersistentErrorMessage
            if (lastError != null) { // Only show toast and clear if there was an error
                preferencesManager!!.clearOpenAILastPersistentError()
                updateOpenAIErrorDisplay() // Refresh the display
                Toast.makeText(
                    requireActivity(),
                    getString(R.string.pref_openai_error_dismiss_confirmation),
                    Toast.LENGTH_SHORT
                ).show()
            }
            true
        }
        updateOpenAIErrorDisplay() // Initial call to set up display
    }

    private fun updateCustomApiUrlVisibility(source: String) {
        if (customApiUrlPreference != null) {
            customApiUrlPreference!!.isVisible = "custom" == source
        }
    }

    private fun updateOpenAIErrorDisplay() {
        if (openAIErrorDisplayPreference == null || preferencesManager == null) {
            return
        }
        val errorMessage = preferencesManager!!.openAILastPersistentErrorMessage
        val errorTimestamp = preferencesManager!!.openAILastPersistentErrorTimestamp
        // Define a threshold, e.g., show errors from last 7 days
        val sevenDaysInMillis = 7 * 24 * 60 * 60 * 1000L
        if (errorMessage != null && System.currentTimeMillis() - errorTimestamp < sevenDaysInMillis) {
            openAIErrorDisplayPreference!!.isVisible = true
            openAIErrorDisplayPreference!!.title =
                getString(R.string.pref_openai_status_alert_title) + " (Click to dismiss)"
            openAIErrorDisplayPreference!!.summary = errorMessage
            // Optionally set an error icon: openAIErrorDisplayPreference.setIcon(R.drawable.ic_error_warning);
        } else {
            openAIErrorDisplayPreference!!.title =
                getString(R.string.pref_openai_status_alert_title) // Reset title
            openAIErrorDisplayPreference!!.summary =
                getString(R.string.pref_openai_status_ok_summary)
            // openAIErrorDisplayPreference.setIcon(null);
            openAIErrorDisplayPreference!!.isVisible =
                true // Or false if you prefer to hide it when no error
        }
    }

    private fun loadOpenAIModels() {
        if (openAIModelPreference == null || preferencesManager == null) return
        openAIModelPreference!!.summaryProvider = null // Disable provider
        openAIModelPreference!!.summary = getString(R.string.pref_openai_model_loading)
        openAIModelPreference!!.isEnabled = false
        if (!preferencesManager!!.isOpenAIRepliesEnabled ||
            TextUtils.isEmpty(preferencesManager!!.openAIApiKey)
        ) {
            openAIModelPreference!!.summaryProvider = null // Disable provider
            openAIModelPreference!!.summary = getString(R.string.pref_openai_model_summary_default)
            openAIModelPreference!!.isEnabled = false
            openAIModelPreference!!.entries = arrayOf()
            openAIModelPreference!!.entryValues = arrayOf()
            return
        }

        // openAIModelPreference.setSummary(getString(R.string.pref_openai_model_loading)); // Already set
        // openAIModelPreference.setEnabled(false); // Already set
        OpenAIHelper.fetchModels(requireActivity(), object : OpenAIHelper.FetchModelsCallback {
            override fun onModelsFetched(models: List<ModelData>?) {
                if (activity == null || preferencesManager == null) return
                val entries: MutableList<CharSequence> = ArrayList()
                val entryValues: MutableList<CharSequence> = ArrayList()
                var foundSelected = false
                // String selectedModelId = preferencesManager.getSelectedOpenAIModel(); // Needs to be added to PrefManager
                // Reading from default shared prefs for now for "pref_openai_model"
                val selectedModelId =
                    preferencesManager!!.selectedOpenAIModel // Using dedicated method
                if (!models.isNullOrEmpty()) {
                    for (model in models) {
                        if (model.id.contains("gpt")) {
                            entries.add(model.id)
                            entryValues.add(model.id)
                            if (model.id == selectedModelId) {
                                foundSelected = true
                            }
                        }
                    }
                    if (entries.isEmpty()) { // Fallback if no "gpt" models
                        for (model in models) {
                            entries.add(model.id)
                            entryValues.add(model.id)
                            if (model.id == selectedModelId) {
                                foundSelected = true
                            }
                        }
                    }
                    if (entries.isNotEmpty()) {
                        openAIModelPreference!!.entries = entries.toTypedArray()
                        openAIModelPreference!!.entryValues = entryValues.toTypedArray()
                        var valueToSet: String? = null
                        valueToSet = if (foundSelected && selectedModelId != null) {
                            selectedModelId
                        } else if (entryValues.isNotEmpty()) {
                            entryValues[0].toString()
                        } else {
                            null
                        }
                        if (valueToSet != null) {
                            openAIModelPreference!!.value = valueToSet
                            preferencesManager!!.saveSelectedOpenAIModel(valueToSet) // Using dedicated method
                            openAIModelPreference!!.summaryProvider =
                                ListPreference.SimpleSummaryProvider.getInstance() // Re-enable default summary behavior
                        } else {
                            openAIModelPreference!!.summaryProvider = null // Disable provider
                            openAIModelPreference!!.summary =
                                getString(R.string.pref_openai_model_not_set)
                        }
                        openAIModelPreference!!.isEnabled = true
                    } else {
                        openAIModelPreference!!.summaryProvider = null // Disable provider
                        openAIModelPreference!!.summary =
                            getString(R.string.pref_openai_model_no_compatible_found)
                        openAIModelPreference!!.entries = arrayOf()
                        openAIModelPreference!!.entryValues = arrayOf()
                        openAIModelPreference!!.isEnabled = false
                    }
                } else { // models list is null or empty from callback
                    openAIModelPreference!!.summaryProvider = null // Disable provider
                    openAIModelPreference!!.summary = getString(R.string.pref_openai_model_error)
                    openAIModelPreference!!.entries = arrayOf()
                    openAIModelPreference!!.entryValues = arrayOf()
                    openAIModelPreference!!.isEnabled = false
                }
            }

            override fun onError(errorMessage: String) {
                if (activity == null) return
                openAIModelPreference!!.summaryProvider = null // Disable provider
                openAIModelPreference!!.summary = errorMessage
                openAIModelPreference!!.isEnabled = false
                openAIModelPreference!!.entries = arrayOf()
                openAIModelPreference!!.entryValues = arrayOf()
                // Toast.makeText(requireActivity(), "Error loading models: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        })
    }

    private fun updateOpenAIApiKeySummary(preference: EditTextPreference?, keyValue: String?) {
        if (preference == null) return
        if (keyValue != null && !keyValue.isEmpty()) {
            // To avoid showing the full key, you could mask it, e.g.:
            // String maskedKey = keyValue.length() > 8 ? keyValue.substring(0, 4) + "..." + keyValue.substring(keyValue.length() - 4) : "Set";
            // preference.setSummary(getString(R.string.pref_openai_api_key_summary_set) + " (" + maskedKey + ")");
            // For now, using the simpler string:
            preference.summary = getString(R.string.pref_openai_api_key_summary_set)
        } else {
            preference.summary = getString(R.string.pref_openai_api_key_summary_not_set)
        }
    }

    override fun onResume() {
        super.onResume()
        if (activity != null) { // Use getActivity() for direct activity access if needed for title
            requireActivity().title = getString(R.string.preference_category_general_label)
        }

        // Refresh models list and state
        if (openAIModelPreference != null) { // Check if initialized
            loadOpenAIModels()
        }
        updateOpenAIErrorDisplay() // Refresh error display on resume
    }

    private fun restartApp() {
        val intent = Intent(requireActivity(), MainActivity::class.java)
        requireActivity().startActivity(intent)
        requireActivity().finishAffinity()
    }
}
