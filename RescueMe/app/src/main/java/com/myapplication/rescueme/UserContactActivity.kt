package com.myapplication.rescueme

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import com.myapplication.rescueme.Helper.Companion.formatContactNumber
import java.io.PrintStream

class UserContactActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_contact)
    }

    fun goToPasscodeActivity(view: View) {
        val contactNumberEt = findViewById<EditText>(R.id.contactNumberEt)
        val contactNumber = formatContactNumber(contactNumberEt.text.toString())

        val nameEt = findViewById<EditText>(R.id.nameEt)
        val name = nameEt.text.toString()

        // 11 characters -> +6512345678
        if (contactNumber.length == 11 && name.isNotEmpty()) {
            saveContactNumber(name, contactNumber)
            val it = Intent(this, FirstPasscodeActivity::class.java)
            startActivity(it)
        } else {
            Toast.makeText(this, "Please enter a valid name/contact number.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveContactNumber(name : String, contactNumber : String) {
        val output = PrintStream(openFileOutput("my_contact.txt", MODE_PRIVATE))
        output.println(name + "\t" + contactNumber)
        output.close()
    }
}