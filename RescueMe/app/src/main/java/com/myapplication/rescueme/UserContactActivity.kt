package com.myapplication.rescueme

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import java.io.PrintStream

class UserContactActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_contact)
    }

    fun goToPasscodeActivity(view: View) {
        val contactNumberEt = findViewById<EditText>(R.id.contactNumberEt)
        val contactNumber = contactNumberEt.text.toString()

        // 11 characters -> +6512345678
        if (contactNumber.length == 11) {
            saveContactNumber(contactNumber)
            val it = Intent(this, FirstPasscodeActivity::class.java)
            startActivity(it)
        } else {
            Toast.makeText(this, "Please enter a valid contact number.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveContactNumber(contactNumber : String) {
        val output = PrintStream(openFileOutput("my_contact.txt", MODE_PRIVATE))
        output.println(contactNumber)
        output.close()
    }
}