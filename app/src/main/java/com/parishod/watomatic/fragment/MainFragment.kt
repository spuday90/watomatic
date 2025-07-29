package com.parishod.watomatic.fragment

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.parishod.watomatic.BuildConfig
import com.parishod.watomatic.NotificationService
import com.parishod.watomatic.R
import com.parishod.watomatic.activity.about.AboutActivity
import com.parishod.watomatic.activity.customreplyeditor.CustomReplyEditorActivity
import com.parishod.watomatic.activity.enabledapps.EnabledAppsActivity
import com.parishod.watomatic.activity.settings.SettingsActivity
import com.parishod.watomatic.adapter.SupportedAppsAdapter
import com.parishod.watomatic.model.App
import com.parishod.watomatic.model.CustomRepliesData
import com.parishod.watomatic.model.preferences.PreferencesManager
import com.parishod.watomatic.model.utils.Constants
import com.parishod.watomatic.model.utils.CustomDialog
import com.parishod.watomatic.model.utils.DbUtils
import com.parishod.watomatic.model.utils.ServieUtils

class MainFragment : Fragment() {
    private var autoReplyTextPreviewCard: CardView? = null
    private var timePickerCard: CardView? = null
    private var autoReplyTextPreview: TextView? = null
    private var timeSelectedTextPreview: TextView? = null
    private var timePickerSubTitleTextPreview: TextView? = null
    private var customRepliesData: CustomRepliesData? = null
    private var autoReplyTextPlaceholder: String? = null
    private var mainAutoReplySwitch: SwitchMaterial? = null
    private var groupReplySwitch: SwitchMaterial? = null
    private var supportedAppsCard: CardView? = null
    private var preferencesManager: PreferencesManager? = null
    private var days = 0
    private val supportedAppsCheckboxes: List<MaterialCheckBox> = ArrayList()
    private val supportedAppsDummyViews: List<View> = ArrayList()
    private var mActivity: Activity? = null
    private var supportedAppsAdapter: SupportedAppsAdapter? = null
    private var enabledApps: MutableList<App> = ArrayList()
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_main, container, false)
        setHasOptionsMenu(true)
        mActivity = activity
        customRepliesData = CustomRepliesData.getInstance(mActivity)
        preferencesManager = PreferencesManager.getPreferencesInstance(mActivity)

        // Assign Views
        mainAutoReplySwitch = view.findViewById(R.id.mainAutoReplySwitch)
        groupReplySwitch = view.findViewById(R.id.groupReplySwitch)
        autoReplyTextPreviewCard = view.findViewById(R.id.mainAutoReplyTextCardView)
        autoReplyTextPreview = view.findViewById(R.id.textView4)
        supportedAppsCard = view.findViewById(R.id.supportedAppsSelectorCardView)
        supportedAppsCard?.setOnClickListener { launchEnabledAppsActivity() }
        val enabledAppsList = view.findViewById<RecyclerView>(R.id.enabled_apps_list)
        val layoutManager = GridLayoutManager(mActivity, getSpanCount(requireContext()))
        enabledAppsList.layoutManager = layoutManager
        supportedAppsAdapter = SupportedAppsAdapter(
            Constants.EnabledAppsDisplayType.HORIZONTAL,
            enabledApps,
        ) { launchEnabledAppsActivity() }
        enabledAppsList.adapter = supportedAppsAdapter
        autoReplyTextPlaceholder = resources.getString(R.string.mainAutoReplyTextPlaceholder)
        timePickerCard = view.findViewById(R.id.replyFrequencyTimePickerCardView)
        timePickerSubTitleTextPreview = view.findViewById(R.id.timePickerSubTitle)
        timeSelectedTextPreview = view.findViewById(R.id.timeSelectedText)
        val imgMinus = view.findViewById<ImageView>(R.id.imgMinus)
        val imgPlus = view.findViewById<ImageView>(R.id.imgPlus)
        autoReplyTextPreviewCard?.setOnClickListener { v: View? ->
            openCustomReplyEditorActivity(
                v
            )
        }
        autoReplyTextPreview?.text = customRepliesData?.textToSendOrElse
        // Enable group chat switch only if main switch id ON
        groupReplySwitch?.isEnabled = mainAutoReplySwitch?.isChecked == true
        mainAutoReplySwitch?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isListenerEnabled(
                    mActivity,
                    NotificationService::class.java
                )
            ) {
//                launchNotificationAccessSettings();
                showPermissionsDialog()
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (!isPostNotificationPermissionGranted) {
                        checkNotificationPermission()
                        return@setOnCheckedChangeListener
                    }
                }
                preferencesManager?.setServicePref(isChecked)
                if (isChecked) {
                    startNotificationService()
                } else {
                    stopNotificationService()
                }
                mainAutoReplySwitch?.setText(
                    if (isChecked) R.string.mainAutoReplySwitchOnLabel else R.string.mainAutoReplySwitchOffLabel
                )
                setSwitchState()

                // Enable group chat switch only if main switch id ON
                groupReplySwitch?.isEnabled = isChecked
            }
        }
        groupReplySwitch?.setOnCheckedChangeListener { _, isChecked ->
            // Ignore if this is not triggered by user action but just UI update in onResume() #62
            if (preferencesManager?.isGroupReplyEnabled == isChecked) {
                return@setOnCheckedChangeListener
            }
            if (isChecked) {
                Toast.makeText(mActivity, R.string.group_reply_on_info_message, Toast.LENGTH_SHORT)
                    .show()
            } else {
                Toast.makeText(
                    mActivity,
                    R.string.group_reply_off_info_message,
                    Toast.LENGTH_SHORT
                ).show()
            }
            preferencesManager?.isGroupReplyEnabled = isChecked
        }
        imgMinus.setOnClickListener {
            if (days > MIN_DAYS) {
                days--
                saveNumDays()
            }
        }
        imgPlus.setOnClickListener {
            if (days < MAX_DAYS) {
                days++
                saveNumDays()
            }
        }
        setNumDays()
        if (!isPostNotificationPermissionGranted) {
            checkNotificationPermission()
        }
        return view
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_REQUEST_CODE
            )
        }
    }

    private val isPostNotificationPermissionGranted: Boolean
        get() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return ContextCompat.checkSelfPermission(
                    requireActivity(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            }
            return true
        }

    private fun showPostNotificationPermissionDeniedSnackbar(view: View) {
        Snackbar.make(
            view,
            requireActivity().resources.getString(R.string.post_notification_permission_snackbar_text),
            Snackbar.LENGTH_INDEFINITE
        )
            .setAction(
                requireActivity().resources.getString(R.string.post_notification_permission_snackbar_setting)
            ) { view1: View ->
                // Open app settings
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", view1.context.packageName, null)
                intent.data = uri
                view1.context.startActivity(intent)
            }.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == NOTIFICATION_REQUEST_CODE) {
            // If permission is granted
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Displaying a toast
            } else {
                // Displaying another toast if permission is not granted
                showPostNotificationPermissionDeniedSnackbar(mainAutoReplySwitch!!)
            }
        }
    }

    private fun getEnabledApps(): List<App> {
        enabledApps.clear()
        enabledApps = ArrayList()
        for (app in Constants.SUPPORTED_APPS) {
            if (preferencesManager?.isAppEnabled(app) == true) {
                enabledApps.add(app)
            }
        }
        return enabledApps
    }

    private fun enableOrDisableEnabledAppsCheckboxes(enabled: Boolean) {
        for (checkbox in supportedAppsCheckboxes) {
            checkbox.isEnabled = enabled
        }
        for (dummyView in supportedAppsDummyViews) {
            dummyView.visibility = if (enabled) View.GONE else View.VISIBLE
        }
    }

    private fun saveNumDays() {
        preferencesManager?.setAutoReplyDelay(days * 24 * 60 * 60 * 1000L) //Save in Milliseconds
        setNumDays()
    }

    private fun setNumDays() {
        val timeDelay =
            preferencesManager?.autoReplyDelay?.div((60 * 1000)) //convert back to minutes
        days = timeDelay?.div((60 * 24))?.toInt()
            ?: 0 //convert back to days
        if (days == 0) {
            timeSelectedTextPreview?.text = "•"
            timePickerSubTitleTextPreview?.setText(R.string.time_picker_sub_title_default)
        } else {
            timeSelectedTextPreview?.text = days.toString()
            timePickerSubTitleTextPreview?.text =
                String.format(resources.getString(R.string.time_picker_sub_title), days)
        }
    }

    override fun onResume() {
        super.onResume()
        //If user directly goes to Settings and removes notifications permission
        //when app is launched check for permission and set appropriate app state
        if (!isListenerEnabled(mActivity, NotificationService::class.java)) {
            preferencesManager?.setServicePref(false)
        }
        setSwitchState()

        // set group chat switch state
        groupReplySwitch?.isChecked = preferencesManager?.isGroupReplyEnabled == true

        // Set user auto reply text
        autoReplyTextPreview?.text = customRepliesData?.textToSendOrElse

        // Update enabled apps list
        supportedAppsAdapter?.updateList(getEnabledApps())
        showAppRatingPopup()
    }

    private fun showAppRatingPopup() {
        val isFromStore = isAppInstalledFromStore(mActivity)
        val status = preferencesManager?.playStoreRatingStatus
        val ratingLastTime = preferencesManager?.playStoreRatingLastTime
        if (isFromStore && status != "Not Interested" && status != "DONE" && System.currentTimeMillis() - (ratingLastTime
                ?: 0) > 10 * 24 * 60 * 60 * 1000L
        ) {
            if (isAppUsedSufficientlyToAskRating()) {
                val customDialog = CustomDialog(mActivity)
                customDialog.showAppLocalRatingDialog { v: View ->
                    showFeedbackPopup(
                        v.tag as Int
                    )
                }
                preferencesManager?.playStoreRatingLastTime = System.currentTimeMillis()
            }
        }
    }

    private val isAppUsedSufficientlyToAskRating: Boolean
        get() {
            val dbUtils = DbUtils(mActivity)
            val firstRepliedTime = dbUtils.firstRepliedTime
            return firstRepliedTime > 0 && System.currentTimeMillis() - firstRepliedTime > 2 * 24 * 60 * 60 * 1000L && dbUtils.nunReplies >= MIN_REPLIES_TO_ASK_APP_RATING
        }

    private fun showFeedbackPopup(rating: Int) {
        val customDialog = CustomDialog(mActivity)
        customDialog.showAppRatingDialog(rating) { v: View ->
            val tag = v.tag as String
            if (tag == requireActivity().resources.getString(R.string.app_rating_goto_store_dialog_button1_title)) {
                //not interested
                preferencesManager?.playStoreRatingStatus = "Not Interested"
            } else if (tag == requireActivity().resources.getString(R.string.app_rating_goto_store_dialog_button2_title)) {
                //Launch playstore rating page
                rateApp()
            } else if (tag == requireActivity().resources.getString(R.string.app_rating_feedback_dialog_mail_button_title)) {
                launchEmailCompose()
            } else if (tag == requireActivity().resources.getString(R.string.app_rating_feedback_dialog_telegram_button_title)) {
                launchFeedbackApp()
            }
        }
    }

    private fun launchEmailCompose() {
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.setDataAndType(Uri.parse("mailto:"), "plain/text") // only email apps should handle this
        intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(Constants.EMAIL_ADDRESS))
        intent.putExtra(Intent.EXTRA_SUBJECT, Constants.EMAIL_SUBJECT)
        if (intent.resolveActivity(requireActivity().packageManager) != null) {
            startActivity(intent)
        }
    }

    private fun launchFeedbackApp() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            launchAppLegacy()
            return
        }
        val isLaunched: Boolean
        isLaunched = try {
            // In order for this intent to be invoked, the system must directly launch a non-browser app.
            // Ref: https://developer.android.com/training/package-visibility/use-cases#avoid-a-disambiguation-dialog
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Constants.TELEGRAM_URL))
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER or
                            Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT
                )
            requireActivity().startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            // This code executes in one of the following cases:
            // 1. Only browser apps can handle the intent.
            // 2. The user has set a browser app as the default app.
            // 3. The user hasn't set any app as the default for handling this URL.
            false
        }
        if (!isLaunched) { // Open Github latest release url in browser if everything else fails
            val url = getString(R.string.watomatic_github_latest_release_url)
            requireActivity().startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(url)))
        }
    }

    private fun launchAppLegacy() {
        if (activity != null) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Constants.TELEGRAM_URL))
            val list = requireActivity().packageManager
                .queryIntentActivities(intent, 0)
            val possibleBrowserIntents = requireActivity().packageManager
                .queryIntentActivities(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("http://www.deekshith.in/")
                    ), 0
                )
            val excludeIntents: MutableSet<String> = HashSet()
            for (eachPossibleBrowserIntent in possibleBrowserIntents) {
                excludeIntents.add(eachPossibleBrowserIntent.activityInfo.name)
            }
            //Check for non browser application
            for (resolveInfo in list) {
                if (!excludeIntents.contains(resolveInfo.activityInfo.name)) {
                    intent.setPackage(resolveInfo.activityInfo.packageName)
                    requireActivity().startActivity(intent)
                    break
                }
            }
        }
    }

    /*
     * Start with rating the app
     * Determine if the Play Store is installed on the device
     *
     * */
    fun rateApp() {
        try {
            val rateIntent = rateIntentForUrl("market://details")
            startActivity(rateIntent)
        } catch (e: ActivityNotFoundException) {
            val rateIntent = rateIntentForUrl("https://play.google.com/store/apps/details")
            startActivity(rateIntent)
        }
    }

    private fun rateIntentForUrl(url: String): Intent {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(String.format("%s?id=%s", url, BuildConfig.APPLICATION_ID))
        )
        val flags =
            Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
        intent.addFlags(flags)
        return intent
    }

    private fun setSwitchState() {
        mainAutoReplySwitch?.isChecked = preferencesManager?.isServiceEnabled == true
        groupReplySwitch?.isEnabled = preferencesManager?.isServiceEnabled == true
        enableOrDisableEnabledAppsCheckboxes(mainAutoReplySwitch?.isChecked == true)
    }

    //https://stackoverflow.com/questions/20141727/check-if-user-has-granted-notificationlistener-access-to-my-app/28160115
    //TODO: Use in UI to verify if it needs enabling or restarting
    fun isListenerEnabled(context: Context?, notificationListenerCls: Class<*>?): Boolean {
        val cn = ComponentName(context!!, notificationListenerCls!!)
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return flat != null && flat.contains(cn.flattenToString())
    }

    private fun openCustomReplyEditorActivity(v: View?) {
        val intent = Intent(mActivity, CustomReplyEditorActivity::class.java)
        startActivity(intent)
    }

    private fun openAboutActivity() {
        val intent = Intent(mActivity, AboutActivity::class.java)
        startActivity(intent)
    }

    private fun showPermissionsDialog() {
        val customDialog = CustomDialog(mActivity)
        val bundle = Bundle()
        bundle.putString(
            Constants.PERMISSION_DIALOG_TITLE,
            getString(R.string.permission_dialog_title)
        )
        bundle.putString(Constants.PERMISSION_DIALOG_MSG, getString(R.string.permission_dialog_msg))
        customDialog.showDialog(bundle, null) { _, which ->
            if (which == -2) {
                //Decline
                showPermissionDeniedDialog()
            } else {
                //Accept
                launchNotificationAccessSettings()
            }
        }
    }

    private fun showPermissionDeniedDialog() {
        val customDialog = CustomDialog(mActivity)
        val bundle = Bundle()
        bundle.putString(
            Constants.PERMISSION_DIALOG_DENIED_TITLE,
            getString(R.string.permission_dialog_denied_title)
        )
        bundle.putString(
            Constants.PERMISSION_DIALOG_DENIED_MSG,
            getString(R.string.permission_dialog_denied_msg)
        )
        bundle.putBoolean(Constants.PERMISSION_DIALOG_DENIED, true)
        customDialog.showDialog(bundle, null) { _, which ->
            if (which == -2) {
                //Decline
                setSwitchState()
            } else {
                //Accept
                launchNotificationAccessSettings()
            }
        }
    }

    fun launchNotificationAccessSettings() {
        //We should remove it few versions later
        enableService(true) //we need to enable the service for it so show in settings
        val notificationListenerSettings: String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            } else {
                "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
            }
        val i = Intent(notificationListenerSettings)
        startActivityForResult(i, REQ_NOTIFICATION_LISTENER)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_NOTIFICATION_LISTENER) {
            if (isListenerEnabled(mActivity, NotificationService::class.java)) {
                Toast.makeText(mActivity, "Permission Granted", Toast.LENGTH_LONG).show()
                startNotificationService()
                preferencesManager?.setServicePref(true)
            } else {
                Toast.makeText(mActivity, "Permission Denied", Toast.LENGTH_LONG).show()
                preferencesManager?.setServicePref(false)
            }
            setSwitchState()
        }
    }

    private fun enableService(enable: Boolean) {
        val packageManager = requireActivity().packageManager
        val componentName = ComponentName(requireActivity(), NotificationService::class.java)
        val settingCode =
            if (enable) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        // enable dummyActivity (as it is disabled in the manifest.xml)
        packageManager.setComponentEnabledSetting(
            componentName,
            settingCode,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun startNotificationService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || preferencesManager?.isForegroundServiceNotificationEnabled == true) {
            ServieUtils.getInstance(mActivity).startNotificationService()
        }
    }

    private fun stopNotificationService() {
        ServieUtils.getInstance(mActivity).stopNotificationService()
    }

    private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = requireActivity().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                Log.i("isMyServiceRunning?", true.toString() + "")
                return true
            }
        }
        Log.i("isMyServiceRunning?", false.toString() + "")
        return false
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        requireActivity().menuInflater.inflate(R.menu.main_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.about) {
            openAboutActivity()
        } else if (item.itemId == R.id.setting) {
            loadSettingsActivity()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadSettingsActivity() {
        val intent = Intent(mActivity, SettingsActivity::class.java)
        requireActivity().startActivity(intent)
    }

    private fun launchEnabledAppsActivity() {
        val intent = Intent(mActivity, EnabledAppsActivity::class.java)
        requireActivity().startActivity(intent)
    }

    override fun onDestroy() {
        stopNotificationService()
        super.onDestroy()
    }

    companion object {
        private const val REQ_NOTIFICATION_LISTENER = 100
        private const val NOTIFICATION_REQUEST_CODE = 101
        fun getSpanCount(context: Context): Int {
            val displayMetrics = context.resources.displayMetrics
            val dpWidth = displayMetrics.widthPixels / displayMetrics.density
            val scalingFactor = 35 // You can vary the value held by the scalingFactor
            return (dpWidth / scalingFactor).toInt()
        }

        //REF: https://stackoverflow.com/questions/37539949/detect-if-an-app-is-installed-from-play-store
        fun isAppInstalledFromStore(context: Context?): Boolean {
            // A list with valid installers package name
            val validInstallers: List<String> =
                ArrayList(listOf("com.android.vending", "com.google.android.feedback"))
            return try {
                // The package name of the app that has installed your app
                val installer = context?.packageManager?.getInstallerPackageName(context.packageName)

                // true if your app has been downloaded from Play Store
                installer != null && validInstallers.contains(installer)
            } catch (e: Exception) {
                false
            }
        }
    }
}
