package com.anurag.onlinemeet.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.anurag.onlinemeet.R
import com.anurag.onlinemeet.databinding.ActivityHomeBinding
import com.google.firebase.auth.FirebaseAuth

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFirebase()
        setupClickListeners()
    }

    private fun setupFirebase() {
        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user != null) {
            binding.tvUserEmail.text = "Welcome, ${user.email}"
        }
    }

    private fun setupClickListeners() {

        binding.btnNewMeeting.setOnClickListener {
            val roomId = generateRoomId()
            Toast.makeText(this, "Room ID: $roomId", Toast.LENGTH_LONG).show()

            val intent = Intent(this, RoomCreateActivity::class.java)
            intent.putExtra("ROOM_ID", roomId)
            startActivity(intent)
        }

        binding.btnJoinMeeting.setOnClickListener {
            startActivity(Intent(this, JoinMeetingActivity::class.java))
        }

        binding.tvLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }


    private fun generateRoomId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..8)
            .map { chars.random() }
            .joinToString("")
    }
}
