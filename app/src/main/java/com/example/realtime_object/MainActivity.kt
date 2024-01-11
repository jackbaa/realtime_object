package com.example.realtime_object

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.example.realtime_object.ml.SsdMobilenetV11Metadata1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // Initialize variables
    private lateinit var labels: List<String>
    private val colors = listOf<Int>(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
        Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED
    )
    private val paint = Paint()
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private var isCameraFreezed: Boolean = false
    private var startup: Boolean = true
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var bitmap: Bitmap
    private lateinit var imageView: ImageView
    private lateinit var cameraDevice: CameraDevice
    private lateinit var handler: Handler
    private lateinit var cameraManager: CameraManager
    private lateinit var textureView: TextureView
    private lateinit var model: SsdMobilenetV11Metadata1
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var btnCapture: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request camera permission
        getPermission()

        // Load labels and initialize image processor and model
        labels = FileUtil.loadLabels(this, "labels.txt")
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()
        model = SsdMobilenetV11Metadata1.newInstance(this)

        // Start handler thread for camera operations
        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        // Initialize UI elements
        imageView = findViewById(R.id.imageView)
        textureView = findViewById(R.id.textureView)

        // Set up texture view listener for camera preview
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                processTextureUpdate()
            }
        }

        // Initialize camera manager, text-to-speech, and capture button
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        textToSpeech = TextToSpeech(this, this)
        btnCapture = findViewById(R.id.btnCapture)
        btnCapture.setOnClickListener { captureImage() }
    }

    // Process updates from the texture view (camera preview)
    private fun processTextureUpdate() {
        // Process the captured image using the model
        bitmap = textureView.bitmap!!
        var image = TensorImage.fromBitmap(bitmap)
        image = imageProcessor.process(image)

        val outputs = model.process(image)
        val locations = outputs.locationsAsTensorBuffer.floatArray
        val classes = outputs.classesAsTensorBuffer.floatArray
        val scores = outputs.scoresAsTensorBuffer.floatArray

        // Create a mutable copy of the bitmap for drawing
        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)

        val h = mutable.height
        val w = mutable.width
        paint.textSize = h / 15f
        paint.strokeWidth = h / 85f

        var x = 0
        // Iterate through detected objects
        scores.forEachIndexed { index, fl ->
            x = index
            x *= 4
            if (fl > 0.5) {
                // Draw bounding box and label on the bitmap
                paint.color = colors[index % colors.size]
                paint.style = Paint.Style.STROKE
                canvas.drawRect(
                    RectF(
                        locations[x + 1] * w,
                        locations[x] * h,
                        locations[x + 3] * w,
                        locations[x + 2] * h
                    ),
                    paint
                )
                paint.style = Paint.Style.FILL
                val label = labels[classes[index].toInt()]
                val confidence = fl.toString()
                val text = "$label $confidence"
                canvas.drawText(
                    text,
                    locations[x + 1] * w,
                    locations[x] * h,
                    paint
                )

                // Text-to-speech for detected objects
                val spokenText = "$label detected with confidence $confidence"
                speakOut(spokenText)
            }
        }

        // Display the processed bitmap in the image view
        imageView.setImageBitmap(mutable)

        // Speak initial message only on startup
        if (startup) {
            val spokenText = "Please press the screen to start detection"
            textToSpeech.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, "")
            startup = false
        }
    }

    // Capture image using the camera
    private fun captureImage() {
        if (!isCameraFreezed && textureView.isAvailable) {
            val surfaceTexture = textureView.surfaceTexture
            val surface = Surface(surfaceTexture)

            try {
                val captureRequestBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                captureRequestBuilder.addTarget(surface)

                cameraDevice.createCaptureSession(
                    listOf(surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            try {
                                // Capture the image and freeze the camera for 5 seconds
                                session.capture(
                                    captureRequestBuilder.build(),
                                    object : CameraCaptureSession.CaptureCallback() {
                                        override fun onCaptureCompleted(
                                            session: CameraCaptureSession,
                                            request: CaptureRequest,
                                            result: TotalCaptureResult
                                        ) {
                                            isCameraFreezed = true
                                            Handler().postDelayed({
                                                isCameraFreezed = false
                                                openCamera()
                                            }, 5000)
                                            session.stopRepeating()
                                        }
                                    },
                                    null
                                )
                            } catch (e: CameraAccessException) {
                                e.printStackTrace()
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {}
                    },
                    null
                )
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }
    }

    // Speak out the given text using text-to-speech
    private fun speakOut(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
        } else {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    // Clean up resources on activity destroy
    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.stop()
        textToSpeech.shutdown()
    }

    // Handle text-to-speech initialization
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Handle language missing data or not supported
            }
        } else {
            // Handle TextToSpeech initialization failure
        }
    }

    // Open the camera for preview
    @SuppressLint("MissingPermission")
    private fun openCamera() {
        cameraManager.openCamera(cameraManager.cameraIdList[0], object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device

                val surfaceTexture = textureView.surfaceTexture
                val surface = Surface(surfaceTexture)

                val captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequest.addTarget(surface)

                cameraDevice.createCaptureSession(
                    listOf(surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            session.setRepeatingRequest(captureRequest.build(), null, null)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            // Handle configuration failure
                        }
                    },
                    handler
                )
            }

            override fun onDisconnected(device: CameraDevice) {}

            override fun onError(device: CameraDevice, error: Int) {
                // Handle camera device error
            }
        }, handler)
    }

    // Request camera permission
    private fun getPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 101)
        }
    }

    // Handle camera permission result
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            getPermission()
        }
    }
}
