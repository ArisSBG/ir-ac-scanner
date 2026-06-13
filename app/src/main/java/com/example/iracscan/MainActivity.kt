package com.example.iracscan

import android.app.Activity
import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

data class IrCandidate(val name: String, val frequency: Int, val pattern: IntArray)

class MainActivity : Activity() {

    // On déclare irManager comme nullable pour éviter le crash si absent
    private var irManager: ConsumerIrManager? = null
    private lateinit var statusText: TextView
    private lateinit var detailText: TextView
    private var currentIndex = 0

    private fun necPattern(address: Int, command: Int): IntArray {
        val UNIT = 562
        val result = mutableListOf<Int>()
        result.add(UNIT * 16)
        result.add(UNIT * 8)
        fun addByte(byte: Int) {
            for (i in 0 until 8) {
                val bit = (byte shr i) and 1
                result.add(UNIT)
                result.add(if (bit == 1) UNIT * 3 else UNIT)
            }
        }
        addByte(address)
        addByte(address.inv() and 0xFF)
        addByte(command)
        addByte(command.inv() and 0xFF)
        result.add(UNIT)
        return result.toIntArray()
    }

    private val candidates: List<IrCandidate> by lazy {
        listOf(
            IrCandidate(
                name = "Essai 1 : TCL (NEC addr=0x57 cmd=0x0F)",
                frequency = 38000,
                pattern = necPattern(0x57, 0x0F)
            ),
            IrCandidate(
                name = "Essai 2 : Chigo (NEC addr=0x66 cmd=0x02)",
                frequency = 38000,
                pattern = necPattern(0x66, 0x02)
            ),
            IrCandidate(
                name = "Essai 3 : Midea simplifié (NEC addr=0xB2 cmd=0xC3)",
                frequency = 38000,
                pattern = necPattern(0xB2, 0xC3)
            ),
            IrCandidate(
                name = "Essai 4 : TCL variante 2 (NEC addr=0x57 cmd=0x40)",
                frequency = 38000,
                pattern = necPattern(0x57, 0x40)
            ),
            IrCandidate(
                name = "Essai 5 : Chigo variante 2 (NEC addr=0x11 cmd=0x01)",
                frequency = 38000,
                pattern = necPattern(0x11, 0x01)
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Construction UI
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 120, 48, 48)
        }

        statusText = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            text = "Initialisation..."
        }

        detailText = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 48)
            text = ""
        }

        val sendButton = Button(this).apply {
            text = "Tester le code suivant"
            isEnabled = false
            setOnClickListener { sendNextCandidate() }
        }

        root.addView(statusText)
        root.addView(detailText)
        root.addView(sendButton)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        // Initialisation IR protégée dans un try/catch
        try {
            val service = getSystemService(Context.CONSUMER_IR_SERVICE)
            if (service != null) {
                irManager = service as ConsumerIrManager
                if (irManager!!.hasIrEmitter()) {
                    statusText.text = "✅ Émetteur IR détecté !\n${candidates.size} codes à tester."
                    detailText.text = "Pointez vers la clim et appuyez sur le bouton."
                    sendButton.isEnabled = true
                } else {
                    statusText.text = "⚠️ Service IR présent\nmais aucun émetteur physique détecté."
                    detailText.text = "Le téléphone ne dispose pas d'IR blaster exploitable via l'API Android."
                }
            } else {
                statusText.text = "❌ Service ConsumerIR introuvable."
                detailText.text = "Ce téléphone n'expose pas l'API IR à Android.\nPlan B nécessaire (Arduino / ESP32)."
            }
        } catch (e: Exception) {
            statusText.text = "❌ Erreur : ${e.javaClass.simpleName}"
            detailText.text = e.message ?: "Erreur inconnue"
        }
    }

    private fun sendNextCandidate() {
        val ir = irManager ?: return

        if (currentIndex >= candidates.size) {
            statusText.text = "🔚 Tous les codes testés."
            detailText.text = "Aucun code n'a fonctionné.\nContactez l'assistant pour enrichir la liste."
            return
        }

        val candidate = candidates[currentIndex]
        statusText.text = candidate.name
        detailText.text = "Fréquence : ${candidate.frequency} Hz\nBits : ${candidate.pattern.size}"

        try {
            ir.transmit(candidate.frequency, candidate.pattern)
            Toast.makeText(
                this,
                "Envoyé (${currentIndex + 1}/${candidates.size})",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur envoi : ${e.message}", Toast.LENGTH_LONG).show()
            detailText.text = "Erreur : ${e.message}"
        }

        currentIndex++
    }
}
