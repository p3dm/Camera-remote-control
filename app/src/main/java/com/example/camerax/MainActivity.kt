package com.example.camerax

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.example.camerax.databinding.ActivityMainBinding
import com.example.camerax.server.CameraSocketServer
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.view.animation.AnimationUtils
import androidx.annotation.RequiresApi


typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null
    private var cameraSocketServer: CameraSocketServer? = null
    private lateinit var cameraExecutor: ExecutorService
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null


    private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {
        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy) {

            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()

            listener(luma)

            image.close()
        }
    }
    private val activityResultLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        // Handle Permission granted/rejected
        var permissionGranted = true
        permissions.entries.forEach {
            if (it.key in REQUIRED_PERMISSIONS && !it.value)
                permissionGranted = false
        }
        if (!permissionGranted) {
            Toast.makeText(
                baseContext,
                "Permission request denied",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private enum class CaptureMode {
        PHOTO,
        VIDEO
    }
    private var currentMode = CaptureMode.PHOTO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        viewBinding.modeSelectorGroup.setOnCheckedChangeListener { _, _ ->
            updateCaptureButtonUI()
        }

        viewBinding.photoViewButton.setOnClickListener {
            openGallery()
        }

        viewBinding.captureButton.setOnClickListener{
            if (currentMode == CaptureMode.PHOTO) {
                takePhoto()
                // Thêm hiệu ứng flash cho nút chụp
                it.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in))
            } else {
                captureVideo()
            }
        }
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
            startSocketServer()
        } else {
            requestPermissions()
        }

        updateCaptureButtonUI()

    }
    private fun updateCaptureButtonUI() {
        runOnUiThread {
            currentMode = if(viewBinding.modeSelectorGroup.checkedRadioButtonId == R.id.photo_mode_button){
                CaptureMode.PHOTO
            }else{
                CaptureMode.VIDEO
            }

            viewBinding.captureButton.apply {
                when (currentMode) {
                    CaptureMode.PHOTO -> {
                        setImageResource(0)
                        setBackgroundResource(R.drawable.capture_button)
                        contentDescription = context.getString(R.string.take_photo)
                    }
                    CaptureMode.VIDEO -> {
                        val isRecording = recording != null
                        // Use a conditional to select resources to avoid nested if-else blocks
                        val backgroundRes = if (isRecording) R.drawable.recording_button else R.drawable.video_button
                        val descriptionRes = if (isRecording) R.string.stop_capture else R.string.start_capture

                        setBackgroundResource(backgroundRes)
                        contentDescription = context.getString(descriptionRes)
                    }
                }
            }
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            // QUAN TRỌNG: Dòng này sẽ tạo/lưu ảnh vào thư mục "Pictures/CameraX"
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "CameraX")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }
                override fun onImageSaved(output: ImageCapture.OutputFileResults){
                    val savedUri = output.savedUri
                    if (savedUri != null) {
                        // Cập nhật thumbnail lên nút thư viện
                        runOnUiThread {
                            viewBinding.photoViewButton.setImageURI(savedUri)
                            viewBinding.photoViewButton.clipToOutline = true
                        }
                    }
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Log.d(TAG, msg)
                }
            }
        )
    }

    @RequiresApi(Build.VERSION_CODES.GINGERBREAD_MR1)
    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        viewBinding.modeSelectorGroup.isEnabled = false

        val curRecording = recording
        if (curRecording != null) {
            curRecording.stop()
            recording = null
            return
        }

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "CameraX")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(this@MainActivity,
                        Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        updateCaptureButtonUI()
                        viewBinding.modeSelectorGroup.isEnabled = false
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            recording = null
                            updateCaptureButtonUI()
                            viewBinding.modeSelectorGroup.isEnabled = true

                            val savedUri = recordEvent.outputResults.outputUri
                            if(savedUri != null){
                                cameraExecutor.execute {
                                    val retriever = android.media.MediaMetadataRetriever()
                                    try{
                                        retriever.setDataSource(this@MainActivity,savedUri)
                                        val savedVideo = retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                        runOnUiThread {
                                            viewBinding.photoViewButton.setImageBitmap(savedVideo)
                                            viewBinding.photoViewButton.clipToOutline = true
                                        }
                                    }catch(e: Exception){
                                        Log.e(TAG,"Lỗi xuất video: ${e.message}")
                                    } finally {
                                        retriever.release() //giải phóng frame khỏi bộ nhớ
                                    }

                                }
                            }
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: " +
                                    "${recordEvent.error}")
                        }
                        recording = null
                        updateCaptureButtonUI()
                        viewBinding.modeSelectorGroup.isEnabled = true
                    }
                }
            }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = viewBinding.viewFinder.surfaceProvider
                }
            //video capture
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)


            imageCapture = ImageCapture.Builder().build()

//            val imageAnalyzer = ImageAnalysis.Builder()
//                .build()
//                .also {
//                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
//                        Log.d(TAG, "Average luminosity: $luma")
//                    })
//                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview,imageCapture, videoCapture)


            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun openGallery(){
        val intent = Intent(Intent.ACTION_VIEW)
        intent.type = "image/*"
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startSocketServer(){
        if(cameraSocketServer == null){
            cameraSocketServer = CameraSocketServer(cameraExecutor) { command ->
                runOnUiThread {
                    handleRemoteCommand(command)
                }
            }
            cameraSocketServer?.start()
        }

    }
    private fun handleRemoteCommand(command: String) {
        when (command.uppercase()) {
            "TAKE_PHOTO" -> {
                viewBinding.photoModeButton.isChecked = true
                Log.d(TAG, "Executing takePhoto() via remote command.")
                takePhoto()
            }
            "RECORD" -> {
                Log.d(TAG, "Executing captureVideo() via remote command.")
                val isRecording = recording != null
                val action = if (isRecording) "Stopping" else "Starting"
                viewBinding.videoModeButton.isChecked = true
                captureVideo()
            }
            else -> {
                Log.w(TAG, "Unknown command received: $command")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "dd-MM-yyyy-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }



}


