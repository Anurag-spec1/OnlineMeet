package com.anurag.onlinemeet.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.anurag.onlinemeet.R
import com.anurag.onlinemeet.databinding.ActivityRoomCreateBinding

class RoomCreateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoomCreateBinding
    private var roomId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRoomCreateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        roomId = intent.getStringExtra("ROOM_ID")

        setupUI()
        setupClickListeners()
    }

    private fun setupUI() {
        binding.tvRoomId.text = roomId?.uppercase()
    }

    private fun setupClickListeners() {

        binding.btnStartCall.setOnClickListener {
            val intent = Intent(this, VideoCallActivity::class.java).apply {
                putExtra("ROOM_ID", roomId)
                putExtra("IS_HOST", true)
            }
            startActivity(intent)
        }

        binding.btnShare.setOnClickListener {
            val shareText = "Join my meeting: $roomId"

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }
            startActivity(Intent.createChooser(shareIntent, "Share via"))
        }

        binding.tvBack.setOnClickListener {
            finish()
        }
    }
}
