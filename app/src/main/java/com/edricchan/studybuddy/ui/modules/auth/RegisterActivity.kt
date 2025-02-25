package com.edricchan.studybuddy.ui.modules.auth

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.core.view.isVisible
import com.edricchan.studybuddy.R
import com.edricchan.studybuddy.annotations.AppDeepLink
import com.edricchan.studybuddy.annotations.WebDeepLink
import com.edricchan.studybuddy.databinding.ActivityRegisterBinding
import com.edricchan.studybuddy.extensions.TAG
import com.edricchan.studybuddy.extensions.editTextStrValue
import com.edricchan.studybuddy.extensions.isInvalidEmail
import com.edricchan.studybuddy.extensions.showSnackbar
import com.edricchan.studybuddy.extensions.startActivity
import com.edricchan.studybuddy.ui.common.BaseActivity
import com.edricchan.studybuddy.ui.modules.main.MainActivity
import com.edricchan.studybuddy.ui.widget.NoSwipeBehavior
import com.edricchan.studybuddy.utils.SharedUtils
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

@WebDeepLink(["/register"])
@AppDeepLink(["/register"])
class RegisterActivity : BaseActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        auth = Firebase.auth

        binding.apply {
            signInBtn.setOnClickListener {
                startActivity<LoginActivity>()
                finish()
            }

            signUpBtn.setOnClickListener {
                val email = emailTextInputLayout.editTextStrValue
                val password = passwordTextInputLayout.editTextStrValue

                if (email.isBlank()) {
                    if (
                        emailTextInputLayout.error.isNullOrEmpty() ||
                        emailTextInputLayout.error == getString(R.string.edittext_errors_invalid_email)
                    ) {
                        emailTextInputLayout.error = getString(R.string.edittext_errors_empty_email)
                    }
                } else if (email.isInvalidEmail()) {
                    if (
                        emailTextInputLayout.error.isNullOrEmpty() ||
                        emailTextInputLayout.error == getString(R.string.edittext_errors_empty_email)
                    ) {
                        emailTextInputLayout.error =
                            getString(R.string.edittext_errors_invalid_email)
                    }
                } else {
                    if (emailTextInputLayout.error != null) {
                        if (emailTextInputLayout.error!!.isNotEmpty()) {
                            emailTextInputLayout.error = null
                        }
                    }
                }
                if (password.isEmpty()) {
                    if (
                        passwordTextInputLayout.error.isNullOrEmpty() ||
                        passwordTextInputLayout.error == getString(R.string.edittext_errors_invalid_password)
                    ) {
                        passwordTextInputLayout.error =
                            getString(R.string.edittext_errors_empty_password)
                    }
                } else if (password.length < 6) {
                    if (
                        passwordTextInputLayout.error.isNullOrEmpty() ||
                        passwordTextInputLayout.error == getString(R.string.edittext_errors_empty_password)
                    ) {
                        passwordTextInputLayout.error =
                            getString(R.string.edittext_errors_invalid_password)
                    }
                } else {
                    if (passwordTextInputLayout.error != null) {
                        if (passwordTextInputLayout.error!!.isNotEmpty()) {
                            passwordTextInputLayout.error = null
                        }
                    }
                }
                if (
                    !passwordTextInputLayout.error.isNullOrEmpty() ||
                    !emailTextInputLayout.error.isNullOrEmpty()
                ) {
                    return@setOnClickListener
                }

                progressBar.isVisible = true
                // Assume that email and password are non-null
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this@RegisterActivity) { task ->
                        progressBar.isVisible = false
                        if (task.isSuccessful) {
                            startActivity<MainActivity>()
                            finish()
                        } else {
                            // TODO: i18n message
                            showSnackbar(
                                coordinatorLayoutRegister,
                                "An error occurred while authenticating. Please try again later.",
                                Snackbar.LENGTH_LONG
                            )
                            Log.e(TAG, "An error occurred while authenticating.", task.exception)
                        }
                    }

            }
        }
        checkNetwork()
    }

    override fun onResume() {
        super.onResume()
        binding.progressBar.isVisible = false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkNetwork() {
        if (SharedUtils.isNetworkAvailable(this)) {
            setViewsEnabled(true)
        } else {
            setViewsEnabled(false)
            // TODO: i18n message
            showSnackbar(
                binding.coordinatorLayoutRegister,
                "No internet connection available. Some actions are disabled",
                Snackbar.LENGTH_INDEFINITE
            ) {
                behavior = NoSwipeBehavior()
                setAction(R.string.dialog_action_retry) { checkNetwork() }
            }
        }
    }

    /**
     * Sets all views as shown/hidden
     *
     * @param enabled Whether to show the views
     */
    private fun setViewsEnabled(enabled: Boolean) {
        binding.apply {
            signUpBtn.isEnabled = enabled
            signInBtn.isEnabled = enabled
            emailTextInputLayout.isEnabled = enabled
            passwordTextInputLayout.isEnabled = enabled
        }
    }
}
