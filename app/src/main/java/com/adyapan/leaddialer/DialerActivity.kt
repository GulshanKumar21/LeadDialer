package com.adyapan.leaddialer

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.ContactsContract
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView

class DialerActivity : AppCompatActivity() {

    private lateinit var tvNumber: TextView

    private lateinit var saveCard: MaterialCardView
    private lateinit var callCard: MaterialCardView
    private lateinit var deleteCard: MaterialCardView

    private lateinit var btnDelete: ImageButton

    private var currentNumber = ""

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_dialer)

        // Back button
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // Views

        tvNumber = findViewById(R.id.tvNumber)

        saveCard = findViewById(R.id.saveCard)
        callCard = findViewById(R.id.callCard)
        deleteCard = findViewById(R.id.deleteCard)

        btnDelete = findViewById(R.id.btnDelete)

        // Initially hidden

        saveCard.visibility = View.GONE
        callCard.visibility = View.GONE
        deleteCard.visibility = View.GONE

        // Number buttons

        val buttons = listOf(
            R.id.btn0 to "0",
            R.id.btn1 to "1",
            R.id.btn2 to "2",
            R.id.btn3 to "3",
            R.id.btn4 to "4",
            R.id.btn5 to "5",
            R.id.btn6 to "6",
            R.id.btn7 to "7",
            R.id.btn8 to "8",
            R.id.btn9 to "9",
            R.id.btnStar to "*",
            R.id.btnHash to "#"
        )

        buttons.forEach { (id, value) ->

            val btn = findViewById<View>(id)

            btn.setOnClickListener {

                animateButton(btn)

                vibrate()

                appendNumber(value)
            }
        }

        // Delete button

        btnDelete.setOnClickListener {

            animateButton(btnDelete)

            vibrate()

            if (currentNumber.isNotEmpty()) {

                currentNumber = currentNumber.dropLast(1)

                tvNumber.text = currentNumber

                if (currentNumber.isEmpty()) {

                    hideActionButtons()
                }
            }
        }

        // Long press clear

        btnDelete.setOnLongClickListener {

            animateButton(btnDelete)

            vibrate()

            currentNumber = ""

            tvNumber.text = ""

            hideActionButtons()

            Toast.makeText(
                this,
                "Cleared",
                Toast.LENGTH_SHORT
            ).show()

            true
        }

        // Call button

        val btnCall = findViewById<ImageButton>(R.id.btnCall)

        btnCall.setOnClickListener {

            animateButton(btnCall)

            vibrate()

            if (currentNumber.isEmpty()) {

                Toast.makeText(
                    this,
                    "Enter number",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            makeCall(currentNumber)
        }


        val btnSave = findViewById<ImageButton>(R.id.btnSave)

        btnSave.setOnClickListener {

            animateButton(btnSave)

            vibrate()

            if (currentNumber.isEmpty()) {

                Toast.makeText(
                    this,
                    "Enter number first",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            saveContact(currentNumber)
        }
    }

    // Append number

    private fun appendNumber(number: String) {

        currentNumber += number

        tvNumber.text = currentNumber

        if (currentNumber.isNotEmpty()) {

            showActionButtons()
        }
    }

    // Show buttons

    private fun showActionButtons() {

        // Delete

        if (deleteCard.visibility != View.VISIBLE) {

            deleteCard.alpha = 0f

            deleteCard.scaleX = 0.8f
            deleteCard.scaleY = 0.8f

            deleteCard.visibility = View.VISIBLE

            deleteCard.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180)
                .start()
        }

        // Save

        if (saveCard.visibility != View.VISIBLE) {

            saveCard.alpha = 0f

            saveCard.translationX = 40f

            saveCard.visibility = View.VISIBLE

            saveCard.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(220)
                .start()
        }

        // Call

        if (callCard.visibility != View.VISIBLE) {

            callCard.alpha = 0f

            callCard.scaleX = 0.7f
            callCard.scaleY = 0.7f

            callCard.translationY = 80f

            callCard.visibility = View.VISIBLE

            callCard.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(260)
                .start()
        }
    }

    // Hide buttons

    private fun hideActionButtons() {

        // Delete

        deleteCard.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(120)
            .withEndAction {

                deleteCard.visibility = View.GONE
            }
            .start()

        // Save

        saveCard.animate()
            .alpha(0f)
            .translationX(40f)
            .setDuration(150)
            .withEndAction {

                saveCard.visibility = View.GONE
            }
            .start()

        // Call

        callCard.animate()
            .alpha(0f)
            .scaleX(0.7f)
            .scaleY(0.7f)
            .translationY(80f)
            .setDuration(180)
            .withEndAction {

                callCard.visibility = View.GONE
            }
            .start()
    }

    // Button animation

    private fun animateButton(view: View) {

        val scaleX = ObjectAnimator.ofFloat(
            view,
            "scaleX",
            1f,
            0.90f,
            1f
        )

        val scaleY = ObjectAnimator.ofFloat(
            view,
            "scaleY",
            1f,
            0.90f,
            1f
        )

        scaleX.duration = 100
        scaleY.duration = 100

        scaleX.start()
        scaleY.start()
    }

    // Vibration
    @Suppress("DEPRECATION")
    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ — use VibratorManager
            val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            val effect = VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE)
            vm.defaultVibrator.vibrate(effect)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(18)
        }
    }


    // Save contact

    private fun saveContact(number: String) {

        val intent = Intent(
            Intent.ACTION_INSERT
        )

        intent.type = ContactsContract.Contacts.CONTENT_TYPE

        intent.putExtra(
            ContactsContract.Intents.Insert.PHONE,
            number
        )

        startActivity(intent)
    }

    // Make call

    private fun makeCall(number: String) {

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE),
                101
            )

            return
        }

        // Register this call with CallManager so the Receiver tracks it
        // and triggers CallPopupActivity to save it.
        val manualLead = Lead(
            id = 0,
            name = "Manual Dial",
            phone = number,
            status = "Pending",
        )
        CallManager.currentLead = manualLead
        CallManager.callActive = true

        CallManager.placeCall(this, manualLead) { intent ->
            startActivity(intent)
        }
    }
}