package com.myapplication.rescueme

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import java.io.PrintStream
import java.security.MessageDigest
import java.util.*
import kotlin.collections.ArrayList

class FirstPasscodeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_first_passcode)
    }

    fun String.toMD5(): String {
        val bytes = MessageDigest.getInstance("MD5").digest(this.toByteArray())
        return bytes.toHex()
    }

    fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }

    // hash the passcode, save to file, then go to HomeActivity
    fun goToHomeActivity(view: View) {
        val passcode = findViewById<EditText>(R.id.passcodeEditText)

        // Check if passcode is of 4 digits then can hash
        if (passcode.text.toString().length == 4) {
            val hashedPasscode = passcode.text.toString().toMD5() // hashes the passcode
            savePasscode(hashedPasscode) // save to file
        } else {
            Toast.makeText(this, "Please enter a 4-digit passcode.", Toast.LENGTH_LONG).show()
        }

        // Only if file with passcode exist, then go HomActivity.
        if (fileExist("passcode.txt")) {
            val it = Intent(this, HomeActivity::class.java)
            startActivity(it)
        }
    }

    // save passcode
    private fun savePasscode(passcodeString : String) {
        val output = PrintStream(openFileOutput("passcode.txt", MODE_APPEND))
        output.println(passcodeString)
        output.close()
    }

    private fun fileExist(fname: String?): Boolean {
        val file = baseContext.getFileStreamPath(fname)
        return file.exists()
    }

}