package com.edricchan.studybuddy.ui.modules.updates

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import com.edricchan.studybuddy.BuildConfig
import com.edricchan.studybuddy.R
import com.edricchan.studybuddy.annotations.AppDeepLink
import com.edricchan.studybuddy.constants.Constants
import com.edricchan.studybuddy.constants.MimeTypeConstants
import com.edricchan.studybuddy.constants.sharedprefs.UpdateInfoPrefConstants
import com.edricchan.studybuddy.databinding.ActivityUpdatesBinding
import com.edricchan.studybuddy.extensions.TAG
import com.edricchan.studybuddy.extensions.showSnackbar
import com.edricchan.studybuddy.ui.common.BaseActivity
import com.edricchan.studybuddy.utils.PermissionUtils
import com.edricchan.studybuddy.utils.SharedUtils
import com.github.javiersantos.appupdater.AppUpdaterUtils
import com.github.javiersantos.appupdater.enums.AppUpdaterError
import com.github.javiersantos.appupdater.enums.UpdateFrom
import com.github.javiersantos.appupdater.objects.Update
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.io.File

@AppDeepLink(["/updates"])
class UpdatesActivity : BaseActivity() {
    // Whether the user has pressed the "check for updates" menu item
    private var isChecking = false
    private lateinit var appUpdate: Update
    private lateinit var binding: ActivityUpdatesBinding
    private lateinit var preferences: SharedPreferences

    // SharedPreferences used for the storing of info on when the app was last updated
    private lateinit var updateInfoPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUpdatesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        updateInfoPreferences =
            getSharedPreferences(UpdateInfoPrefConstants.FILE_UPDATE_INFO, Context.MODE_PRIVATE)

        binding.emptyStateCtaBtn.setOnClickListener { checkForUpdates() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_updates, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }

            R.id.action_check_for_updates -> {
                Log.d(TAG, "Check for updates clicked!")
                showSnackbar(
                    binding.coordinatorLayoutUpdates,
                    R.string.update_snackbar_checking,
                    Snackbar.LENGTH_SHORT
                )

                checkForUpdates()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // Disable the menu item when the user has pressed it
        menu.findItem(R.id.action_check_for_updates).isEnabled = !isChecking
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == 0) {
            showUpdateDialog()
        }
    }

    private fun downloadUpdate(
        downloadUrl: String,
        version: String,
        ignoreMobileDataSetting: Boolean = false,
        showMobileDataWarning: Boolean = true,
        downloadAgain: Boolean = false
    ) {
        // The file name that the downloaded APK will use
        val fileName = getString(R.string.download_apk_name, version)

        // Check if the user wants to redownload the APK file or if the user does not have the APK already downloaded
        if (downloadAgain || !File(
                "${
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    )
                }/$fileName"
            ).exists()
        ) {
            // Check if the user has clicked on "download anyway" on the dialog that showed, or
            // check if the user is using a cellular network and has disabled downloading updates over cellular
            if (SharedUtils.isCellularNetworkAvailable(this) && (!ignoreMobileDataSetting && !preferences.getBoolean(
                    Constants.prefUpdatesDownloadOverMetered,
                    false
                )) && showMobileDataWarning
            ) {

                // Show a dialog warning the user that cellular network is on as the user has disabled downloading updates over cellular
                MaterialAlertDialogBuilder(this).apply {
                    setTitle(R.string.update_activity_cannot_download_cellular_dialog_title)
                    setMessage(R.string.update_activity_cannot_download_cellular_dialog_msg)
                    setNegativeButton(R.string.dialog_action_cancel) { dialogInterface: DialogInterface, i: Int -> dialogInterface.dismiss() }
                    setPositiveButton(R.string.update_activity_cannot_download_cellular_dialog_positive_btn) { dialogInterface, i ->
                        dialogInterface.dismiss()
                        // Call the function again, but skip the mobile data warning
                        downloadUpdate(
                            downloadUrl,
                            version,
                            ignoreMobileDataSetting = true,
                            showMobileDataWarning = true,
                            downloadAgain = false
                        )
                    }
                }.show()
            } else {
                // Construct a request to download from a URI
                val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                    // Don't download over roaming
                    // TODO: Add setting for this
                    setAllowedOverRoaming(false)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    // Set the file type so that the package installer can open the file
                    setMimeType(MimeTypeConstants.appPackageArchiveMime)
                }

                val manager = getSystemService<DownloadManager>()
                manager?.enqueue(request)

                // Create a receiver to intercept when the download is complete
                val downloadCompleteReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        Log.d(TAG, "Download complete!")
                        Toast.makeText(context, "Download complete!", Toast.LENGTH_SHORT).show()
                        installUpdate(fileName)
                    }
                }

                // Lastly, register the receiver with the intents that it accepts
                registerReceiver(
                    downloadCompleteReceiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }
        } else {
            MaterialAlertDialogBuilder(this).apply {
                setTitle(R.string.update_activity_update_already_downloaded_dialog_title)
                setMessage(R.string.update_activity_update_already_downloaded_dialog_msg)
                setPositiveButton(R.string.update_activity_update_already_downloaded_dialog_positive_btn) { dialogInterface, _ ->
                    installUpdate(fileName)
                    dialogInterface.dismiss()
                }
                setNegativeButton(R.string.update_activity_update_already_downloaded_dialog_negative_btn) { _, _ ->
                    if (File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                .toString() + "/" + fileName
                        ).delete()
                    ) {
                        Toast.makeText(
                            this@UpdatesActivity,
                            R.string.update_activity_delete_update_success_toast,
                            Toast.LENGTH_LONG
                        ).show()
                        downloadUpdate(
                            downloadUrl,
                            version,
                            ignoreMobileDataSetting = false,
                            showMobileDataWarning = true
                        )
                    } else {
                        Toast.makeText(
                            this@UpdatesActivity,
                            R.string.update_activity_cannot_delete_update_toast,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.show()
        }
    }

    private fun installUpdate(fileName: String) {
        // See https://android-developers.googleblog.com/2017/08/making-it-safer-to-get-apps-on-android-o.html
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            if (packageManager.canRequestPackageInstalls()) {
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    // Set the data for the intent to open
                    setDataAndType(
                        FileProvider.getUriForFile(
                            applicationContext,
                            "${BuildConfig.APPLICATION_ID}.provider",
                            File("${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)}/$fileName")
                        ),
                        MimeTypeConstants.appPackageArchiveMime
                    )
                    // Mark this as a new task and allow reading the file
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK and Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                // Lastly, start the activity
                startActivity(installIntent)
                updateInfoPreferences.edit {
                    putLong(
                        UpdateInfoPrefConstants.PREF_LAST_UPDATED_DATE,
                        System.currentTimeMillis()
                    )
                }
            } else {
                // User has not allowed the app as an unknown app source
                MaterialAlertDialogBuilder(this).apply {
                    setMessage(R.string.update_activity_enable_unknown_sources_dialog_msg)
                    setNegativeButton(R.string.dialog_action_cancel) { dialog, _ -> dialog.dismiss() }
                    setNeutralButton(R.string.dialog_action_retry) { dialog, _ ->
                        dialog.dismiss()
                        // Call this method again
                        installUpdate(fileName)
                    }
                    setPositiveButton(R.string.update_activity_enable_unknown_sources_dialog_positive_btn) { _, _ ->
                        // Create an intent to take the user to Settings for unknown app sources
                        val allowUnknownAppsIntent =
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = Uri.parse("package:$packageName")
                            }
                        startActivity(allowUnknownAppsIntent)
                    }
                    // Mark it as non-dismissable
                    setCancelable(false)
                }.show()
            }
        } else {
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    FileProvider.getUriForFile(
                        applicationContext,
                        "${BuildConfig.APPLICATION_ID}.provider",
                        File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                .toString() + "/" + fileName
                        )
                    ), MimeTypeConstants.appPackageArchiveMime
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK and Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(installIntent)
        }
    }

    private fun showUpdateDialog() {
        MaterialAlertDialogBuilder(this).apply {
            setTitle(getString(R.string.update_dialog_title_new, appUpdate.latestVersion))
            setIcon(R.drawable.ic_system_update_24dp)
            setMessage("What's new:\n${appUpdate.releaseNotes}")
            setNegativeButton(android.R.string.cancel, null)
            setPositiveButton(R.string.update_dialog_positive_btn_text) { _, _ ->
                // Check if the device is running Android Marshmallow or higher
                // Marshmallow introduces the capability for runtime permissions
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (PermissionUtils.getInstance(this@UpdatesActivity).checkPermissionGranted(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    ) {
                        downloadUpdate(appUpdate.urlToDownload.toString(), appUpdate.latestVersion)
                    } else {
                        // User has pressed "Deny" to the permission prompt
                        if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                            MaterialAlertDialogBuilder(this@UpdatesActivity).apply {
                                setTitle(R.string.update_perm_rationale_dialog_title)
                                setMessage(R.string.update_perm_rationale_dialog_msg)
                                setNegativeButton(R.string.update_perm_rationale_dialog_deny) { dialog, _ -> dialog.dismiss() }
                                setPositiveButton(R.string.update_perm_rationale_dialog_grant) { _, _ ->
                                    requestPermissions(
                                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                        0
                                    )
                                }
                            }.show()
                        } else {
                            // Request permissions for writting to the external storage
                            // Note: requestPermissions requires an array of permissions as the first parameter
                            requestPermissions(
                                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                0
                            )
                        }
                    }
                } else {
                    downloadUpdate(appUpdate.urlToDownload.toString(), appUpdate.latestVersion)
                }
            }
            setOnDismissListener {
                // Mark the check for updates menu item as selectable again
                isChecking = false
                invalidateOptionsMenu()
            }
            setOnCancelListener {
                // Mark the check for updates menu item as selectable again
                isChecking = false
                invalidateOptionsMenu()
            }
        }.show()
    }

    private fun checkForUpdates() {
        // Save last updated status
        updateInfoPreferences.edit {
            Log.d(TAG, "Setting last checked for updates date...")
            putLong(
                UpdateInfoPrefConstants.PREF_LAST_CHECKED_FOR_UPDATES_DATE,
                System.currentTimeMillis()
            )
        }
        isChecking = true
        invalidateOptionsMenu()
        val appUpdaterUtils = getUpdateJsonUrl()?.let {
            AppUpdaterUtils(this)
                .setUpdateFrom(UpdateFrom.JSON)
                .setUpdateJSON(it)
                .withListener(object : AppUpdaterUtils.UpdateListener {
                    override fun onSuccess(update: Update, updateAvailable: Boolean?) {
                        appUpdate = update
                        if (update.latestVersionCode == BuildConfig.VERSION_CODE && !updateAvailable!!) {
                            // User is running latest version
                            showSnackbar(
                                binding.coordinatorLayoutUpdates,
                                R.string.update_snackbar_latest,
                                Snackbar.LENGTH_SHORT
                            )
                        } else {
                            showUpdateDialog()
                        }
                    }

                    override fun onFailed(appUpdaterError: AppUpdaterError) {
                        isChecking = false
                        invalidateOptionsMenu()
                        val snackbarMsgRes = when (appUpdaterError) {
                            AppUpdaterError.NETWORK_NOT_AVAILABLE ->
                                R.string.update_snackbar_error_no_internet

                            AppUpdaterError.JSON_ERROR -> R.string.update_snackbar_error_malformed
                            else -> R.string.update_snackbar_error
                        }

                        showSnackbar(
                            binding.coordinatorLayoutUpdates, snackbarMsgRes,
                            Snackbar.LENGTH_LONG
                        ) {
                            setAction(R.string.dialog_action_retry) { checkForUpdates() }
                        }
                    }
                })
        }
        appUpdaterUtils?.start()
    }

    private fun getUpdateJsonUrl(): String? {
        return if (SharedUtils.isDevMode(this)) {
            val mPrefs = PreferenceManager.getDefaultSharedPreferences(this)
            if (mPrefs.getBoolean(Constants.debugUseTestingJsonUrl, true)) {
                mPrefs.getString(
                    Constants.debugSetCustomJsonUrl,
                    getString(R.string.update_json_testing_url)
                )
            } else {
                getString(R.string.update_json_release_url)
            }
        } else {
            getString(R.string.update_json_release_url)
        }
    }
}
