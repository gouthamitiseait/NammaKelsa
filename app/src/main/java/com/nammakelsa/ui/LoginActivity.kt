package com.nammakelsa.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.nammakelsa.R
import com.nammakelsa.repository.FirebaseRepository
import com.nammakelsa.ui.customer.CustomerSearchActivity
import com.nammakelsa.ui.worker.WorkerDashboardActivity
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private lateinit var cardWorker: View
    private lateinit var cardCustomer: View

    private lateinit var etPhoneNumber: EditText
    private lateinit var etOtp: EditText

    private lateinit var btnSendOtp: Button
    private lateinit var btnVerifyOtp: Button
    private lateinit var btnDemoLogin: Button

    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var layoutOtpSection: View

    private val repository = FirebaseRepository()

    private var selectedRole = "worker"
    private var verificationId: String? = null

    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        supportActionBar?.hide()

        initViews()
        setupRoleSelection()
        setupClickListeners()
    }

    private fun initViews() {

        cardWorker = findViewById(R.id.cardWorker)
        cardCustomer = findViewById(R.id.cardCustomer)

        etPhoneNumber = findViewById(R.id.etPhoneNumber)
        etOtp = findViewById(R.id.etOtp)

        btnSendOtp = findViewById(R.id.btnSendOtp)
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp)
        btnDemoLogin = findViewById(R.id.btnDemoLogin)

        progressBar = findViewById(R.id.progressBar)
        tvError = findViewById(R.id.tvError)

        layoutOtpSection = findViewById(R.id.layoutOtpSection)
    }

    private fun setupRoleSelection() {

        selectRole("worker")

        cardWorker.setOnClickListener {
            selectRole("worker")
        }

        cardCustomer.setOnClickListener {
            selectRole("customer")
        }
    }

    private fun selectRole(role: String) {

        selectedRole = role

        cardWorker.alpha =
            if (role == "worker") 1f else 0.5f

        cardCustomer.alpha =
            if (role == "customer") 1f else 0.5f

        layoutOtpSection.visibility = View.VISIBLE
    }

    private fun setupClickListeners() {

        btnSendOtp.setOnClickListener {
            sendOTP()
        }

        btnVerifyOtp.setOnClickListener {
            verifyOTP()
        }

        // Demo Login
        btnDemoLogin.setOnClickListener {

            etPhoneNumber.setText("+919999999999")

            Toast.makeText(
                this,
                "Use OTP: 123456",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // SEND OTP

    private fun sendOTP() {

        var phoneNumber =
            etPhoneNumber.text.toString().trim()

        if (phoneNumber.isBlank()) {

            showError("Enter phone number")
            return
        }

        if (!phoneNumber.startsWith("+91")) {
            phoneNumber = "+91$phoneNumber"
        }

        showProgress(true)
        hideError()

        auth.firebaseAuthSettings
            .setAppVerificationDisabledForTesting(false)

        val options =
            PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(callbacks)
                .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private val callbacks =
        object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(
                credential: PhoneAuthCredential
            ) {

                signInWithCredential(credential)
            }

            override fun onVerificationFailed(
                e: FirebaseException
            ) {

                showProgress(false)

                showError(
                    e.message ?: "OTP failed"
                )
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {

                this@LoginActivity.verificationId =
                    verificationId

                showProgress(false)

                Toast.makeText(
                    this@LoginActivity,
                    "OTP Sent",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    // VERIFY OTP

    private fun verifyOTP() {

        val otp =
            etOtp.text.toString().trim()

        val vId = verificationId

        if (otp.length != 6) {

            showError("Enter valid OTP")
            return
        }

        if (vId == null) {

            showError("Request OTP first")
            return
        }

        showProgress(true)

        val credential =
            PhoneAuthProvider.getCredential(
                vId,
                otp
            )

        signInWithCredential(credential)
    }

    // SIGN IN

    private fun signInWithCredential(
        credential: PhoneAuthCredential
    ) {

        lifecycleScope.launch {

            val result =
                repository.signInWithPhoneCredential(
                    credential
                )

            result.onSuccess {

                showProgress(false)

                saveRoleLocally(selectedRole)

                Toast.makeText(
                    this@LoginActivity,
                    "Login Successful",
                    Toast.LENGTH_SHORT
                ).show()

                navigateToDashboard()
            }

            result.onFailure {

                showProgress(false)

                showError(
                    it.message ?: "Verification Failed"
                )
            }
        }
    }

    // NAVIGATION

    private fun navigateToDashboard() {

        val intent =
            if (selectedRole == "worker") {

                Intent(
                    this,
                    WorkerDashboardActivity::class.java
                )

            } else {

                Intent(
                    this,
                    CustomerSearchActivity::class.java
                )
            }

        startActivity(intent)
        finish()
    }

    private fun saveRoleLocally(role: String) {

        getSharedPreferences(
            "namma_kelsa_prefs",
            MODE_PRIVATE
        ).edit()
            .putString("user_role", role)
            .apply()
    }

    private fun showProgress(show: Boolean) {

        progressBar.visibility =
            if (show) View.VISIBLE else View.GONE

        btnSendOtp.isEnabled = !show
        btnVerifyOtp.isEnabled = !show
    }

    private fun showError(message: String) {

        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    private fun hideError() {

        tvError.visibility = View.GONE
    }
}