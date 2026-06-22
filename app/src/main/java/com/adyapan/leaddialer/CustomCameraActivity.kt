package com.adyapan.leaddialer

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CustomCameraActivity : AppCompatActivity() {

    private val TAG = "CustomCameraActivity"

    private lateinit var previewView: PreviewView
    private lateinit var btnCaptureOuter: View
    private lateinit var btnCaptureInner: View
    private lateinit var btnBack: ImageButton
    private lateinit var progressBar: ProgressBar

    private var imageCapture: ImageCapture? = null
    private var outputUri: Uri? = null
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_camera)

        previewView = findViewById(R.id.previewView)
        btnCaptureOuter = findViewById(R.id.btnCaptureOuter)
        btnCaptureInner = findViewById(R.id.btnCaptureInner)
        btnBack = findViewById(R.id.btnBack)
        progressBar = findViewById(R.id.progressBar)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Get the output Uri passed by the launcher
        outputUri = intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT)
        if (outputUri == null) {
            Toast.makeText(this, "Output URI is missing!", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        btnBack.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        val captureClickListener = View.OnClickListener {
            takePhoto()
        }
        btnCaptureInner.setOnClickListener(captureClickListener)
        btnCaptureOuter.setOnClickListener(captureClickListener)

        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview usecase
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // ImageCapture usecase
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            // Forces FRONT camera only
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind usecases before rebinding
                cameraProvider.unbindAll()

                // Bind usecases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                Toast.makeText(this, "Failed to initialize camera: ${exc.message}", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val uri = outputUri ?: return

        // Show loading progress and disable capture buttons
        progressBar.visibility = View.VISIBLE
        btnCaptureInner.isEnabled = false
        btnCaptureOuter.isEnabled = false

        // Create a local temporary file to capture image directly to disk
        val tempFile = try {
            File.createTempFile("selfie_temp_", ".jpg", cacheDir)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create temp file", e)
            Toast.makeText(this, "Failed to capture: internal storage error", Toast.LENGTH_SHORT).show()
            progressBar.visibility = View.GONE
            btnCaptureInner.isEnabled = true
            btnCaptureOuter.isEnabled = true
            return
        }

        // Setup output options using the local temporary file
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    Toast.makeText(this@CustomCameraActivity, "Capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                    
                    try {
                        if (tempFile.exists()) {
                            tempFile.delete()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete temp file on error", e)
                    }

                    // Reset UI states
                    progressBar.visibility = View.GONE
                    btnCaptureInner.isEnabled = true
                    btnCaptureOuter.isEnabled = true
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    var success = false
                    try {
                        contentResolver.openOutputStream(uri)?.use { outputStream ->
                            FileInputStream(tempFile).use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        success = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to copy photo to destination URI", e)
                    } finally {
                        try {
                            if (tempFile.exists()) {
                                tempFile.delete()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to delete temp file", e)
                        }
                    }

                    if (success) {
                        Log.d(TAG, "Photo capture succeeded: $uri")
                        val resultIntent = Intent().apply {
                            putExtra(MediaStore.EXTRA_OUTPUT, uri)
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    } else {
                        Toast.makeText(this@CustomCameraActivity, "Failed to save photo", Toast.LENGTH_SHORT).show()
                        progressBar.visibility = View.GONE
                        btnCaptureInner.isEnabled = true
                        btnCaptureOuter.isEnabled = true
                    }
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
