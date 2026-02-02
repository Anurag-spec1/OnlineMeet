package com.anurag.onlinemeet.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.anurag.onlinemeet.R
import com.anurag.onlinemeet.databinding.ActivitySignupBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.btnSignup.setOnClickListener {
            doSignup()
        }

        binding.tvLogin.setOnClickListener {
            finish()
        }
    }

    private fun doSignup() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        when {
            name.isEmpty() -> {
                binding.etName.error = "Name required"
                return
            }
            email.isEmpty() -> {
                binding.etEmail.error = "Email required"
                return
            }
            password.length < 6 -> {
                binding.etPassword.error = "Minimum 6 characters"
                return
            }
            password != confirmPassword -> {
                binding.etConfirmPassword.error = "Passwords do not match"
                return
            }
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {

                    val profileUpdates = userProfileChangeRequest {
                        displayName = name
                    }
                    auth.currentUser?.updateProfile(profileUpdates)

                    Toast.makeText(this, "Signup successful!", Toast.LENGTH_SHORT).show()
//                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()

                } else {
                    Toast.makeText(
                        this,
                        task.exception?.message ?: "Signup failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }
}
