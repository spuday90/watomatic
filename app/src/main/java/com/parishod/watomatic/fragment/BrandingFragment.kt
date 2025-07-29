package com.parishod.watomatic.fragment

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.RelativeLayout
import androidx.fragment.app.Fragment
import com.parishod.watomatic.BuildConfig
import com.parishod.watomatic.R
import com.parishod.watomatic.activity.donation.DonationActivity
import com.parishod.watomatic.model.GithubReleaseNotes
import com.parishod.watomatic.model.preferences.PreferencesManager
import com.parishod.watomatic.network.GetReleaseNotesService
import com.parishod.watomatic.network.RetrofitInstance
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.regex.Matcher

class BrandingFragment : Fragment() {
    private var watomaticSubredditBtn: Button? = null
    private var whatsNewBtn: Button? = null
    private var whatsNewUrls: List<String>? = null
    private var gitHubReleaseNotesId = -1
    private val communityUrls = listOf(
        "https://t.me/WatomaticApp",
        "https://fosstodon.org/@watomatic",
        "https://twitter.com/watomatic",
        "https://www.reddit.com/r/watomatic"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_branding, container, false)
        val githubBtn = view.findViewById<ImageButton>(R.id.watomaticGithubBtn)
        val shareLayout = view.findViewById<ImageButton>(R.id.share_btn)
        watomaticSubredditBtn = view.findViewById(R.id.watomaticSubredditBtn)
        whatsNewBtn = view.findViewById(R.id.whatsNewBtn)
        whatsNewBtn?.setOnClickListener {
            launchApp(
                whatsNewUrls,
                getString(R.string.watomatic_github_latest_release_url)
            )
        }
        shareLayout.setOnClickListener { launchShareIntent() }
        watomaticSubredditBtn?.setOnClickListener {
            launchApp(
                communityUrls,
                getString(R.string.watomatic_subreddit_url)
            )
        }
        githubBtn.setOnClickListener {
            val url = getString(R.string.watomatic_github_url)
            startActivity(
                Intent(Intent.ACTION_VIEW).setData(Uri.parse(url))
            )
        }
        getGthubReleaseNotes()
        if (BuildConfig.FLAVOR.equals("Default", ignoreCase = true)) {
            shareLayout.visibility = View.GONE
            val circularProgressBarLayout =
                view.findViewById<RelativeLayout>(R.id.circularProgressBar)
            circularProgressBarLayout.visibility = View.VISIBLE
            circularProgressBarLayout.setOnClickListener {
                val intent = Intent(activity, DonationActivity::class.java)
                startActivity(intent)
            }
        }
        return view
    }

    private fun launchApp(urls: List<String>?, fallbackUrl: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            launchAppLegacy(urls, fallbackUrl)
            return
        }
        var isLaunched = false
        if (urls != null) {
            for (eachReleaseUrl in urls) {
                if (isLaunched) {
                    break
                }
                try {
                    // In order for this intent to be invoked, the system must directly launch a non-browser app.
                    // Ref: https://developer.android.com/training/package-visibility/use-cases#avoid-a-disambiguation-dialog
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(eachReleaseUrl))
                        .setFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER or
                                    Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT
                        )
                    startactivity(intent)
                    isLaunched = true
                } catch (e: ActivityNotFoundException) {
                    // This code executes in one of the following cases:
                    // 1. Only browser apps can handle the intent.
                    // 2. The user has set a browser app as the default app.
                    // 3. The user hasn't set any app as the default for handling this URL.
                    isLaunched = false
                }
            }
        }
        if (!isLaunched) { // Open Github latest release url in browser if everything else fails
            startactivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(fallbackUrl)))
        }
    }

    private fun launchAppLegacy(urls: List<String>?, fallbackUrl: String) {
        var isLaunched = false
        if (urls != null) {
            for (url in urls) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                val list: List<ResolveInfo>? =
                    if (activity != null) requireActivity().packageManager.queryIntentActivities(
                        intent,
                        0
                    ) else null
                val possibleBrowserIntents: List<ResolveInfo>? =
                    if (activity != null) requireActivity().packageManager
                        .queryIntentActivities(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("http://www.deekshith.in/")
                            ), 0
                        ) else null
                val excludeIntents: MutableSet<String> = HashSet()
                if (possibleBrowserIntents != null) {
                    for (eachPossibleBrowserIntent in possibleBrowserIntents) {
                        excludeIntents.add(eachPossibleBrowserIntent.activityInfo.name)
                    }
                }

                //Check for non browser application
                if (list != null) {
                    for (resolveInfo in list) {
                        if (!excludeIntents.contains(resolveInfo.activityInfo.name)) {
                            intent.setPackage(resolveInfo.activityInfo.packageName)
                            startactivity(intent)
                            isLaunched = true
                            break
                        }
                    }
                }
                if (isLaunched) {
                    break
                }
            }
        }
        if (!isLaunched) { // Open Github latest release url in browser if everything else fails
            startactivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(fallbackUrl)))
        }
    }

    private fun startactivity(intent: Intent) {
        PreferencesManager.getPreferencesInstance(activity).setGithubReleaseNotesId(gitHubReleaseNotesId)
        startActivity(intent)
        showHideWhatsNewBtn(false)
    }

    private fun getGthubReleaseNotes() {
        val releaseNotesService =
            RetrofitInstance.retrofitInstance.create(GetReleaseNotesService::class.java)
        val call = releaseNotesService.releaseNotes
        call.enqueue(object : Callback<List<GithubReleaseNotes>?> {
            override fun onResponse(
                call: Call<List<GithubReleaseNotes>?>,
                response: Response<List<GithubReleaseNotes>?>
            ) {
                if (response.body() != null) {
                    parseReleaseNotesResponse(response.body())
                }
            }

            override fun onFailure(call: Call<List<GithubReleaseNotes>?>, t: Throwable) {}
        })
    }

    private fun parseReleaseNotesResponse(releaseNotesList: List<GithubReleaseNotes>?) {
        if (releaseNotesList != null) {
            for (releaseNotes in releaseNotesList) {
                val appVersion = "v" + BuildConfig.VERSION_NAME
                //in the list of release notes, check the release notes for this version of app
                if (releaseNotes.tagName.equals(appVersion, ignoreCase = true)) {
                    gitHubReleaseNotesId = releaseNotes.id
                    val body = releaseNotes.body
                    val gitHubId =
                        PreferencesManager.getPreferencesInstance(activity).githubReleaseNotesId
                    if ((gitHubId == 0 || gitHubId != gitHubReleaseNotesId) && !body.contains("minor-release: true")) {
                        //Split the body into separate lines and search for line starting with "view release notes on"
                        val splitStr = body.split("\n").toTypedArray()
                        if (splitStr.isNotEmpty()) {
                            for (s in splitStr) {
                                if (s.lowercase().startsWith("view release notes on")) {
                                    whatsNewUrls = extractLinks(s)
                                    showHideWhatsNewBtn(true)
                                    break
                                }
                            }
                        }
                        break
                    }
                }
            }
        }
    }

    private fun showHideWhatsNewBtn(show: Boolean) {
        watomaticSubredditBtn?.visibility = if (show) View.GONE else View.VISIBLE
        whatsNewBtn?.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun launchShareIntent() {
        val sharingIntent = Intent(Intent.ACTION_SEND)
        sharingIntent.type = "text/plain"
        sharingIntent.putExtra(
            Intent.EXTRA_SUBJECT,
            resources.getString(R.string.share_subject)
        )
        sharingIntent.putExtra(Intent.EXTRA_TEXT, resources.getString(R.string.share_app_text))
        startActivity(Intent.createChooser(sharingIntent, "Share app via"))
    }

    companion object {
        fun extractLinks(text: String): List<String> {
            val links: MutableList<String> = ArrayList()
            val m = Patterns.WEB_URL.matcher(text)
            while (m.find()) {
                val url = m.group()
                links.add(url)
            }
            return links
        }
    }
}
