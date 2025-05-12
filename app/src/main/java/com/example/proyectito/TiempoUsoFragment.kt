package com.example.proyectito

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment

class TiempoUsoFragment : Fragment() {

    private lateinit var tiempoUsoValue: TextView
    private lateinit var btnIrTiempoActividad: Button
    private var segundos = 0
    private var handler: Handler? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tiempo_uso, container, false)
        tiempoUsoValue = view.findViewById(R.id.tiempoUsoValue)
        btnIrTiempoActividad = view.findViewById(R.id.btnIrTiempoActividad)

        btnIrTiempoActividad.setOnClickListener {
            startActivity(Intent(requireContext(), TiempoActividad::class.java))
        }

        // Simulación de tiempo de uso (puedes reemplazarlo por el real)
        handler = Handler(Looper.getMainLooper())
        handler?.post(object : Runnable {
            override fun run() {
                tiempoUsoValue.text = formatTime(segundos)
                segundos++
                handler?.postDelayed(this, 1000)
            }
        })

        return view
    }

    private fun formatTime(segundos: Int): String {
        val h = segundos / 3600
        val m = (segundos % 3600) / 60
        val s = segundos % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler?.removeCallbacksAndMessages(null)
    }
}