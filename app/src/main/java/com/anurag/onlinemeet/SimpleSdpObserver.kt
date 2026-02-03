
package com.anurag.onlinemeet

import org.webrtc.SessionDescription
import org.webrtc.SdpObserver

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String) {}
    override fun onSetFailure(error: String) {}
}