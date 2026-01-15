package com.example.camerax.server.camera

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import com.example.camerax.MainActivity
import com.example.camerax.R
import com.example.camerax.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService

class CameraController(private val activity: MainActivity,
                       private val viewBinding: ActivityMainBinding,
                       private val cameraExecutor: ExecutorService) {

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private enum class CaptureMode {
        PHOTO,
        VIDEO,
    }
    private enum class CameraSite{
        FRONT,
        BACK
    }
    private var currentMode = CaptureMode.PHOTO
    private var currentSite = CameraSite.BACK
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.surfaceProvider = viewBinding.viewFinder.surfaceProvider
                    }

                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)

                imageCapture = ImageCapture.Builder().build()

                val cameraSelector = if (currentSite == CameraSite.BACK) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }

                // 5. Unbind và Bind
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    activity,
                    cameraSelector,
                    preview,
                    imageCapture,
                    videoCapture
                )

                Log.d(TAG, "Camera started with site: $currentSite")

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(activity))
    }
    fun takePhoto() {
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
            .Builder(activity.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(activity),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }
                override fun onImageSaved(output: ImageCapture.OutputFileResults){
                    val savedUri = output.savedUri
                    if (savedUri != null) {
                        // Cập nhật thumbnail lên nút thư viện
                        activity.runOnUiThread {
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
    fun onCaptureButtonClicked(view: View) {
        if (currentMode == CaptureMode.PHOTO) {
            takePhoto()
            view.startAnimation(
                AnimationUtils.loadAnimation(
                    activity,
                    android.R.anim.fade_in
                )
            )
        } else {
            captureVideo()
        }
    }

    @RequiresApi(Build.VERSION_CODES.GINGERBREAD_MR1)
    fun captureVideo() {
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
            .Builder(activity.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        recording = videoCapture.output
            .prepareRecording(activity, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(activity,
                        Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(activity)) { recordEvent ->
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
                                    val retriever = MediaMetadataRetriever()
                                    try{
                                        retriever.setDataSource(activity,savedUri)
                                        val savedVideo = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                        activity.runOnUiThread {
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

    fun openGallery(){
        val intent = Intent(Intent.ACTION_VIEW)
        intent.type = "image/*"
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        activity.startActivity(intent)
    }
    fun updateCaptureButtonUI() {
        activity.runOnUiThread {
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
    fun isRecording(): Boolean = recording != null

    fun startOrToggleVideoFromRemote() {
        // Dùng lại captureVideo nhưng không có animation
        captureVideo()
    }

    fun switchToPhotoMode() {
        viewBinding.photoModeButton.isChecked = true
        currentMode = CaptureMode.PHOTO
        updateCaptureButtonUI()
    }
    fun flipCamera(){
        viewBinding.flipCameraButton.animate().rotation(180f).setDuration(300).start()
        if(currentSite == CameraSite.BACK){
            currentSite = CameraSite.FRONT
            lensFacing = CameraSelector.LENS_FACING_FRONT
        }
        else{
            currentSite = CameraSite.BACK
            lensFacing = CameraSelector.LENS_FACING_BACK
        }
        startCamera()
    }
    fun switchToVideoMode() {
        viewBinding.videoModeButton.isChecked = true
        currentMode = CaptureMode.VIDEO
        updateCaptureButtonUI()
    }

    companion object {
        private const val TAG = "CameraController"
        private const val FILENAME_FORMAT = "dd-MM-yyyy-HH-mm-ss-SSS"
    }

}