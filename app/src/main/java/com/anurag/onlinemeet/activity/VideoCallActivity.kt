package com.anurag.onlinemeet.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.anurag.onlinemeet.R
import com.anurag.onlinemeet.databinding.ActivityVideoCallBinding
import com.anurag.onlinemeet.IceCandidateModel
import com.anurag.onlinemeet.SimpleSdpObserver
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import org.webrtc.*

class VideoCallActivity : AppCompatActivity(), PeerConnection.Observer {

    private lateinit var binding: ActivityVideoCallBinding
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private lateinit var database: FirebaseDatabase
    private lateinit var roomRef: DatabaseReference

    private var roomId: String? = null
    private var isHost = false
    private var videoCapturer: VideoCapturer? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private val eglBase by lazy { EglBase.create() }
    private val PERMISSION_REQUEST_CODE = 1001
    private var remoteVideoTrack: VideoTrack? = null
    private var remoteAudioTrack: AudioTrack? = null
    private var isPeerConnectionCreated = false
    private var remoteSdpProcessed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        roomId = intent.getStringExtra("ROOM_ID")
        isHost = intent.getBooleanExtra("IS_HOST", false)

        binding.tvRoomId.text = "Room: $roomId"
        binding.tvStatus.text = "Initializing..."

        checkPermissions()
    }

    private fun checkPermissions() {
        val cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val micPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)

        if (cameraPermission == PackageManager.PERMISSION_GRANTED && micPermission == PackageManager.PERMISSION_GRANTED) {
            initializeCall()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            initializeCall()
        } else {
            Toast.makeText(this, "Camera and microphone permissions are required!", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun initializeCall() {
        initWebRTC()
        initFirebase()
        setupVideoRenderers()
        setupControls()

        startLocalMedia()
    }

    private fun initWebRTC() {
        val options = PeerConnectionFactory.InitializationOptions.builder(this)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()

        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext,
            true,
            true
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        binding.tvStatus.text = "WebRTC Ready"
        Log.d("VideoCall", "WebRTC initialized")
    }

    private fun createPeerConnection() {
        if (isPeerConnectionCreated) return

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED

        peerConnection = peerConnectionFactory.createPeerConnection(
            rtcConfig,
            this
        )

        if (peerConnection == null) {
            Log.e("VideoCall", "Failed to create peer connection")
            Toast.makeText(this, "Failed to create connection", Toast.LENGTH_SHORT).show()
            return
        }

        isPeerConnectionCreated = true
        binding.tvStatus.text = "Peer connection created"
        Log.d("VideoCall", "Peer connection created successfully")

        localAudioTrack?.let { peerConnection?.addTrack(it) }
        localVideoTrack?.let { peerConnection?.addTrack(it) }

        if (isHost) {
            createAndSendOffer()
        }
    }

    private fun startLocalMedia() {
        try {
            val audioConstraints = MediaConstraints()
            val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
            localAudioTrack = peerConnectionFactory.createAudioTrack("AUDIO_TRACK", audioSource)
            localAudioTrack?.setEnabled(true)

            videoCapturer = createVideoCapturer()
            if (videoCapturer == null) {
                Log.e("VideoCall", "No camera available")
                Toast.makeText(this, "No camera found", Toast.LENGTH_SHORT).show()
                return
            }

            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            videoSource = peerConnectionFactory.createVideoSource(false)

            videoCapturer?.initialize(surfaceTextureHelper, applicationContext, videoSource?.capturerObserver)
            videoCapturer?.startCapture(640, 480, 30)

            localVideoTrack = peerConnectionFactory.createVideoTrack("VIDEO_TRACK", videoSource)
            localVideoTrack?.setEnabled(true)
            localVideoTrack?.addSink(binding.localVideoView)

            binding.tvStatus.text = "Local camera ON"
            Log.d("VideoCall", "Local media started successfully")

            createPeerConnection()

        } catch (e: Exception) {
            Log.e("VideoCall", "Media error: ${e.message}", e)
            Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createVideoCapturer(): VideoCapturer? {
        return try {
            val enumerator = Camera2Enumerator(this)
            val deviceNames = enumerator.deviceNames

            for (deviceName in deviceNames) {
                if (enumerator.isFrontFacing(deviceName)) {
                    return enumerator.createCapturer(deviceName, null)
                }
            }

            for (deviceName in deviceNames) {
                val videoCapturer = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }

            null
        } catch (e: Exception) {
            Log.e("VideoCall", "Camera error: ${e.message}")
            null
        }
    }

    private fun initFirebase() {
        database = FirebaseDatabase.getInstance()
        roomRef = database.getReference("rooms/$roomId")

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        roomRef.child("participants/$uid").setValue(mapOf("joined" to true))

        roomRef.child("participants").addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                handleParticipantData(snapshot)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                handleParticipantData(snapshot)
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                runOnUiThread {
                    binding.tvStatus.text = "Friend disconnected"
                    Toast.makeText(this@VideoCallActivity, "Friend disconnected", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e("VideoCall", "Firebase cancelled: ${error.message}")
            }
        })
    }

    private fun handleParticipantData(snapshot: DataSnapshot) {
        val uid = snapshot.key ?: return
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        if (uid == myUid) return
        Log.d("VideoCall", "Processing data from participant: $uid")

        snapshot.child("sdp").getValue(String::class.java)?.let { sdp ->
            if (!remoteSdpProcessed) {
                remoteSdpProcessed = true
                Log.d("VideoCall", "Received remote SDP")
                handleRemoteSdp(sdp)
            }
        }

        snapshot.child("ice").children.forEach { iceSnapshot ->
            val candidate = iceSnapshot.getValue(IceCandidateModel::class.java)
            candidate?.let { iceCandidateModel ->
                val iceCandidate = IceCandidate(
                    iceCandidateModel.sdpMid,
                    iceCandidateModel.sdpMLineIndex,
                    iceCandidateModel.sdp
                )
                Log.d("VideoCall", "Adding ICE candidate: ${iceCandidate.sdpMid}")
                peerConnection?.addIceCandidate(iceCandidate)
            }
        }
    }

    private fun uploadSdp(sdp: SessionDescription) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        roomRef.child("participants/$uid/sdp").setValue(sdp.description)
            .addOnSuccessListener {
                Log.d("VideoCall", "SDP uploaded: ${sdp.type}")
            }
            .addOnFailureListener { e ->
                Log.e("VideoCall", "Failed to upload SDP: ${e.message}")
            }
    }

    private fun uploadIceCandidate(candidate: IceCandidate) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val model = IceCandidateModel(
            sdpMid = candidate.sdpMid,
            sdpMLineIndex = candidate.sdpMLineIndex,
            sdp = candidate.sdp
        )
        roomRef.child("participants/$uid/ice").push().setValue(model)
            .addOnSuccessListener {
                Log.d("VideoCall", "ICE candidate uploaded: ${candidate.sdpMid}")
            }
    }

    private fun handleRemoteSdp(sdpString: String) {
        Log.d("VideoCall", "Handling remote SDP")

        val remoteSdp = SessionDescription(SessionDescription.Type.OFFER, sdpString)

        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                Log.d("VideoCall", "Remote description set successfully")

                if (!isHost) {
                    Log.d("VideoCall", "Creating answer as guest")
                    val constraints = MediaConstraints().apply {
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                    }

                    peerConnection?.createAnswer(object : SimpleSdpObserver() {
                        override fun onCreateSuccess(answer: SessionDescription) {
                            Log.d("VideoCall", "Answer created successfully")
                            peerConnection?.setLocalDescription(SimpleSdpObserver(), answer)
                            uploadSdp(answer)
                            runOnUiThread {
                                binding.tvStatus.text = "Answer sent"
                            }
                        }

                        override fun onCreateFailure(error: String) {
                            Log.e("VideoCall", "Failed to create answer: $error")
                        }
                    }, constraints)
                } else {
                    runOnUiThread {
                        binding.tvStatus.text = "Received answer from guest"
                    }
                }
            }

            override fun onSetFailure(error: String) {
                Log.e("VideoCall", "Failed to set remote description: $error")
                try {
                    val answerSdp = SessionDescription(SessionDescription.Type.ANSWER, sdpString)
                    peerConnection?.setRemoteDescription(SimpleSdpObserver(), answerSdp)
                } catch (e: Exception) {
                    Log.e("VideoCall", "Failed to parse as answer: ${e.message}")
                }
            }
        }, remoteSdp)
    }

    private fun createAndSendOffer() {
        Log.d("VideoCall", "Host creating offer...")

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                Log.d("VideoCall", "Offer created successfully")
                peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        Log.d("VideoCall", "Local description set for offer")
                        uploadSdp(sdp)
                        runOnUiThread {
                            binding.tvStatus.text = "Offer sent, waiting for answer..."
                        }
                    }

                    override fun onSetFailure(error: String) {
                        Log.e("VideoCall", "Failed to set local description: $error")
                    }
                }, sdp)
            }

            override fun onCreateFailure(error: String) {
                Log.e("VideoCall", "Failed to create offer: $error")
            }
        }, constraints)
    }

    private fun setupVideoRenderers() {
        binding.remoteVideoView.init(eglBase.eglBaseContext, null)
        binding.remoteVideoView.setMirror(false)
        binding.remoteVideoView.setEnableHardwareScaler(true)

        binding.localVideoView.init(eglBase.eglBaseContext, null)
        binding.localVideoView.setMirror(true)
        binding.localVideoView.setEnableHardwareScaler(true)

        Log.d("VideoCall", "Video renderers setup complete")
    }

    private fun setupControls() {
        binding.btnEndCall.setOnClickListener { endCall() }
        binding.btnToggleMic.setOnClickListener { toggleMic() }
        binding.btnToggleCamera.setOnClickListener { toggleCamera() }
        binding.btnSwitchCamera.setOnClickListener { switchCamera() }
    }

    private fun toggleMic() {
        val isEnabled = localAudioTrack?.enabled() ?: false
        localAudioTrack?.setEnabled(!isEnabled)
        binding.btnToggleMic.setImageResource(
            if (!isEnabled) R.drawable.ic_mic else R.drawable.ic_mic
        )
        Toast.makeText(this, if (!isEnabled) "Mic ON" else "Mic OFF", Toast.LENGTH_SHORT).show()
    }

    private fun toggleCamera() {
        val isEnabled = localVideoTrack?.enabled() ?: false
        localVideoTrack?.setEnabled(!isEnabled)
        binding.btnToggleCamera.setImageResource(
            if (!isEnabled) R.drawable.ic_videocam else R.drawable.ic_videocam
        )
        Toast.makeText(this, if (!isEnabled) "Camera ON" else "Camera OFF", Toast.LENGTH_SHORT).show()
    }

    private fun switchCamera() {
        Toast.makeText(this, "Switched camera", Toast.LENGTH_SHORT).show()
    }

    private fun endCall() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        surfaceTextureHelper?.dispose()
        peerConnection?.close()

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        uid?.let {
            roomRef.child("participants/$it").removeValue()
        }

        localVideoTrack?.removeSink(binding.localVideoView)
        remoteVideoTrack?.removeSink(binding.remoteVideoView)
        binding.localVideoView.release()
        binding.remoteVideoView.release()
        eglBase.release()

        finish()
    }



    override fun onIceCandidate(candidate: IceCandidate) {
        Log.d("VideoCall", "onIceCandidate: ${candidate.sdpMid}")
        uploadIceCandidate(candidate)
    }

    override fun onAddStream(stream: MediaStream) {
        Log.d("VideoCall", "onAddStream: ${stream.id}")
        runOnUiThread {
            stream.videoTracks.firstOrNull()?.let { track ->
                remoteVideoTrack = track
                remoteVideoTrack?.addSink(binding.remoteVideoView)
                Log.d("VideoCall", "Remote video track added: ${track.id()}")
            }

            stream.audioTracks.firstOrNull()?.let { track ->
                remoteAudioTrack = track
                remoteAudioTrack?.setEnabled(true)
                Log.d("VideoCall", "Remote audio track added: ${track.id()}")
            }

            binding.tvStatus.text = "✅ CONNECTED! Video call live!"
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSignalingChange(state: PeerConnection.SignalingState?) {
        Log.d("VideoCall", "Signaling state changed: $state")
    }

    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
        Log.d("VideoCall", "ICE connection state changed: $state")
        when (state) {
            PeerConnection.IceConnectionState.CONNECTED -> {
                runOnUiThread {
                    binding.tvStatus.text = "ICE Connected!"
                }
            }
            PeerConnection.IceConnectionState.COMPLETED -> {
                runOnUiThread {
                    binding.tvStatus.text = "ICE Completed!"
                }
            }
            PeerConnection.IceConnectionState.DISCONNECTED -> {
                runOnUiThread {
                    binding.tvStatus.text = "Disconnected"
                }
            }
            PeerConnection.IceConnectionState.FAILED -> {
                runOnUiThread {
                    binding.tvStatus.text = "Connection failed"
                    Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {}
        }
    }

    override fun onIceConnectionReceivingChange(receiving: Boolean) {
        Log.d("VideoCall", "ICE connection receiving changed: $receiving")
    }

    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
        Log.d("VideoCall", "ICE gathering state changed: $state")
    }

    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
        Log.d("VideoCall", "ICE candidates removed: ${candidates?.size}")
    }

    override fun onRemoveStream(stream: MediaStream?) {
        Log.d("VideoCall", "Stream removed")
        runOnUiThread {
            binding.tvStatus.text = "Stream ended"
            remoteVideoTrack?.removeSink(binding.remoteVideoView)
            remoteVideoTrack = null
            remoteAudioTrack = null
        }
    }

    override fun onDataChannel(dataChannel: DataChannel?) {
        Log.d("VideoCall", "Data channel: $dataChannel")
    }

    override fun onRenegotiationNeeded() {
        Log.d("VideoCall", "Renegotiation needed")
    }

    override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
        Log.d("VideoCall", "Track added: ${receiver?.track()?.id()}")
        runOnUiThread {
            receiver?.track()?.let { track ->
                if (track is VideoTrack) {
                    remoteVideoTrack?.removeSink(binding.remoteVideoView)
                    remoteVideoTrack = track
                    remoteVideoTrack?.addSink(binding.remoteVideoView)
                    Log.d("VideoCall", "Video track added via onAddTrack")
                } else if (track is AudioTrack) {
                    remoteAudioTrack = track
                    remoteAudioTrack?.setEnabled(true)
                    Log.d("VideoCall", "Audio track added via onAddTrack")
                }
            }
        }
    }

    override fun onDestroy() {
        endCall()
        super.onDestroy()
    }
}