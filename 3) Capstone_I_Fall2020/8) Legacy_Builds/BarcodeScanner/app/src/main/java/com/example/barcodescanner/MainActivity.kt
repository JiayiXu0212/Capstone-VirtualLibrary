package com.example.barcodescanner

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.Detector.Detections
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var surfaceView: SurfaceView
    private lateinit var barcodeDetector: BarcodeDetector
    private lateinit var cameraSource: CameraSource
    private var toneGen1: ToneGenerator? = null
    private var barcodeText: TextView? = null
    private var barcodeData: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        toneGen1 = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        surfaceView = findViewById(R.id.surface_view)
        barcodeText = findViewById(R.id.barcode_text)
        initialiseDetectorsAndSources()
    }

    private fun initialiseDetectorsAndSources() {

        //Toast.makeText(getApplicationContext(), "Barcode scanner started", Toast.LENGTH_SHORT).show();
        barcodeDetector = BarcodeDetector.Builder(this)
            .setBarcodeFormats(Barcode.ALL_FORMATS)
            .build()
        cameraSource = CameraSource.Builder(this, barcodeDetector)
            .setRequestedPreviewSize(1920, 1080)
            .setAutoFocusEnabled(true) //you should add this feature
            .build()
        surfaceView!!.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                try {
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        cameraSource.start(surfaceView!!.holder)
                    } else {
                        ActivityCompat.requestPermissions(
                            this@MainActivity,
                            arrayOf(Manifest.permission.CAMERA),
                            REQUEST_CAMERA_PERMISSION
                        )
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                cameraSource.stop()
            }
        })
        barcodeDetector.setProcessor(object : Detector.Processor<Barcode> {
            override fun release() {
                // Toast.makeText(getApplicationContext(), "To prevent memory leaks barcode scanner has been stopped", Toast.LENGTH_SHORT).show();
            }

            override fun receiveDetections(detections: Detections<Barcode>) {
                val barcodes = detections.detectedItems
                if (barcodes.size() != 0) {
                    barcodeText!!.post {
                        if (barcodes.valueAt(0).email != null) {
                            barcodeText!!.removeCallbacks(null)
                            barcodeData = barcodes.valueAt(0).email.address
                            barcodeText!!.text = barcodeData
                            toneGen1!!.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                        } else {
                            barcodeData = barcodes.valueAt(0).displayValue
                            barcodeText!!.text = barcodeData
                            toneGen1!!.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                        }
                    }
                }
            }
        })
    }

    override fun onPause() {
        super.onPause()
        supportActionBar!!.hide()
        cameraSource!!.release()
    }

    override fun onResume() {
        super.onResume()
        supportActionBar!!.hide()
        initialiseDetectorsAndSources()
    }

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 201
    }
}