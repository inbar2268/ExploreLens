package com.example.explorelens

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.PixelCopy
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Session
import com.google.ar.core.exceptions.*
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.ux.TransformableNode
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var arFragment: CustomArFragment
    private lateinit var captureButton: Button
    private val CAMERA_PERMISSION_CODE = 1
    private val TAG = "ExploreAR"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Ensure Google Play Services for AR is installed first
        if (!checkGooglePlayServicesForAr()) {
            Log.e(TAG, "Google Play Services for AR is not available")
            Toast.makeText(this, "Google Play Services for AR is required", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Check for camera permission before initializing AR components
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            initArComponents()
        }
    }

    private fun checkGooglePlayServicesForAr(): Boolean {
        val availability = ArCoreApk.getInstance().checkAvailability(this)
        Log.d(TAG, "ARCore availability: $availability")

        // If ARCore is not installed at all, request installation
        if (availability == ArCoreApk.Availability.UNKNOWN_CHECKING ||
            availability == ArCoreApk.Availability.UNKNOWN_ERROR ||
            availability == ArCoreApk.Availability.UNKNOWN_TIMED_OUT) {
            Toast.makeText(this, "ARCore availability cannot be determined", Toast.LENGTH_LONG).show()
            return false
        }

        if (availability.isTransient) {
            // Re-check after a small delay
            Handler(Looper.getMainLooper()).postDelayed({
                checkGooglePlayServicesForAr()
            }, 200)
            return false
        }

        try {
            when (availability) {
                ArCoreApk.Availability.SUPPORTED_INSTALLED -> {
                    // ARCore is installed and supported
                    Log.d(TAG, "ARCore is supported and installed")
                    return true
                }
                ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
                ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                    // Request ARCore installation or update
                    Log.d(TAG, "Requesting ARCore installation")
                    try {
                        // Request ARCore installation with the correct method signature
                        val installStatus = ArCoreApk.getInstance().requestInstall(
                            this,  // Activity
                            true   // User can cancel installation
                        )

                        return when (installStatus) {
                            ArCoreApk.InstallStatus.INSTALLED -> {
                                Log.d(TAG, "ARCore installed successfully")
                                true
                            }
                            ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                                // User has been prompted to install ARCore
                                Log.d(TAG, "ARCore installation has been requested")
                                false
                            }
                            else -> {
                                Log.e(TAG, "Unknown ARCore install status: $installStatus")
                                false
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error requesting ARCore installation: ${e.message}", e)
                        return false
                    }
                }
                else -> {
                    // ARCore is not supported
                    Log.e(TAG, "ARCore is not supported on this device")
                    Toast.makeText(this, "ARCore is not supported on this device", Toast.LENGTH_LONG).show()
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking ARCore: ${e.message}", e)
            return false
        }
    }

    private fun initArComponents() {
        try {
            // Initialize AR Fragment with a safer approach
            arFragment = supportFragmentManager.findFragmentById(R.id.ar_fragment) as? CustomArFragment
                ?: throw IllegalStateException("AR Fragment not found")

            // Setup snapshot button
            captureButton = findViewById(R.id.capture_button)
            captureButton.setOnClickListener {
                takeSnapshot()
            }

            // Setup tap listener to place 3D objects
            arFragment.setOnTapArPlaneListener { hitResult, _, _ ->
                Log.d(TAG, "Plane tapped, placing object")
                placeObject(hitResult.createAnchor())
            }

            Log.d(TAG, "AR components initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AR components: ${e.message}", e)
            Toast.makeText(this, "AR initialization failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun placeObject(anchor: com.google.ar.core.Anchor) {
        Log.d(TAG, "Loading 3D model")

        // Load 3D model with better error handling
        ModelRenderable.builder()
            .setSource(this, R.raw.andy)  // Make sure you have this model in res/raw/
            // Note: Removed setIsFilamentGltf as it's not compatible with your Sceneform version
            .build()
            .thenAccept { renderable ->
                Log.d(TAG, "3D model loaded successfully")
                addNodeToScene(anchor, renderable)
            }
            .exceptionally { throwable ->
                Log.e(TAG, "Unable to load model: ${throwable.message}", throwable)
                Toast.makeText(this, "Unable to load model: ${throwable.localizedMessage}",
                    Toast.LENGTH_SHORT).show()
                null
            }
    }

    private fun addNodeToScene(anchor: com.google.ar.core.Anchor, renderable: ModelRenderable) {
        try {
            // Create anchor and transformable nodes
            val anchorNode = AnchorNode(anchor)
            anchorNode.setParent(arFragment.arSceneView.scene)

            val transformableNode = TransformableNode(arFragment.transformationSystem)
            transformableNode.setParent(anchorNode)
            transformableNode.renderable = renderable
            transformableNode.select()

            Log.d(TAG, "3D object placed in scene")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding node to scene: ${e.message}", e)
        }
    }

    private fun takeSnapshot() {
        try {
            val view = arFragment.arSceneView

            // Create bitmap with the same dimensions as the AR Scene View
            val bitmap = Bitmap.createBitmap(
                view.width, view.height,
                Bitmap.Config.ARGB_8888
            )

            // Use PixelCopy to copy the pixels from the view to the bitmap
            val handler = Handler(Looper.getMainLooper())

            PixelCopy.request(view, bitmap, { copyResult ->
                if (copyResult == PixelCopy.SUCCESS) {
                    Log.d(TAG, "AR snapshot captured successfully")
                    saveImageToGallery(bitmap)
                } else {
                    Log.e(TAG, "Failed to capture AR snapshot: $copyResult")
                    Toast.makeText(
                        this,
                        "Failed to capture AR snapshot",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }, handler)
        } catch (e: Exception) {
            Log.e(TAG, "Error taking snapshot: ${e.message}", e)
        }
    }

    private fun saveImageToGallery(bitmap: Bitmap) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "AR_$timestamp.jpg"

        try {
            // For Android 10 (API 29) and above, use MediaStore
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, imageFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                contentResolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                }
                Log.d(TAG, "AR Snapshot saved to gallery: $imageFileName")
                Toast.makeText(this, "AR Snapshot saved to gallery", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save image: ${e.message}", e)
            Toast.makeText(this, "Failed to save image: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Camera permission granted")
                initArComponents()
            } else {
                Log.e(TAG, "Camera permission denied")
                Toast.makeText(this, "Camera permission is required for AR", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Handle ARCore session resuming with better error control
        if (::arFragment.isInitialized) {
            try {
                val session = arFragment.arSceneView.session
                if (session == null) {
                    Log.d(TAG, "AR session is null in onResume")
                    return
                }

                Log.d(TAG, "Resuming AR session")
                arFragment.arSceneView.resume()
            } catch (e: CameraNotAvailableException) {
                Log.e(TAG, "Camera not available during onResume: ${e.message}", e)
                Toast.makeText(this, "Camera not available. Please restart the app.", Toast.LENGTH_LONG).show()
                finish()
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming AR session: ${e.message}", e)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::arFragment.isInitialized) {
            try {
                Log.d(TAG, "Pausing AR session")
                arFragment.arSceneView.pause()
            } catch (e: Exception) {
                Log.e(TAG, "Error pausing AR session: ${e.message}", e)
            }
        }
    }
}