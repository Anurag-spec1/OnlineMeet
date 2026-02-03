package com.anurag.onlinemeet.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.anurag.onlinemeet.R
import com.anurag.onlinemeet.databinding.ActivityJoinMeetingBinding

class JoinMeetingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJoinMeetingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityJoinMeetingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {

        binding.btnJoinCall.setOnClickListener {
            val roomId = binding.etRoomId.text.toString().trim().uppercase()

            when {
                roomId.isEmpty() -> {
                    Toast.makeText(this, "Meeting ID daalo bhai!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                roomId.length < 6 -> {
                    Toast.makeText(this, "Meeting ID chhota hai!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            val intent = Intent(this, VideoCallActivity::class.java).apply {
                putExtra("ROOM_ID", roomId)
                putExtra("IS_HOST", false)
            }
            startActivity(intent)
        }

        binding.tvBack.setOnClickListener {
            finish()
        }
    }
}
