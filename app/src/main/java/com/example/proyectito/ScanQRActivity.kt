package com.example.proyectito

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class ScanQRActivity : AppCompatActivity() {
    private val CAMERA_PERMISSION_REQUEST = 100

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents == null) {
            Toast.makeText(this, "Escaneo cancelado", Toast.LENGTH_LONG).show()
        } else {
            // Aquí procesaremos el código QR escaneado
            Toast.makeText(this, "Código escaneado: ${result.contents}", Toast.LENGTH_LONG).show()
            // TODO: Procesar el código QR y vincular el dispositivo
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_qr)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST)
        } else {
            startScanner()
        }
    }

    private fun startScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Escanea el código QR del dispositivo del hijo/hija")
            setCameraId(0)  // Usar cámara trasera
            setBeepEnabled(false)
            setBarcodeImageEnabled(true)
        }
        barcodeLauncher.launch(options)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanner()
            } else {
                Toast.makeText(this, "Se requiere permiso de cámara para escanear códigos QR",
                    Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}