package com.example.explorelens

import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.sceneform.ux.ArFragment
import android.util.Log

class CustomArFragment : ArFragment() {

    private val TAG = "CustomArFragment"

    override fun getSessionConfiguration(session: Session): Config {
        val config = super.getSessionConfiguration(session)

        // Important: Configure session to use CPU image for computer vision tasks
        config.focusMode = Config.FocusMode.AUTO
        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL

        // Enable Cloud Anchors if needed
        // config.cloudAnchorMode = Config.CloudAnchorMode.ENABLED

        // This is critical for some devices
        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE

        // Enable depth API if available
        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.depthMode = Config.DepthMode.AUTOMATIC
        }

        // Important: Disable unused features for more compatibility
        config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY

        Log.d(TAG, "AR session configured with focused optimization")
        return config
    }

    override fun isArRequired(): Boolean {
        // Optional: Return false to allow the app to work even if AR isn't fully supported
        // This would let your app run in non-AR mode
        return true
    }
}