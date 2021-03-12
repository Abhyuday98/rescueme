package com.myapplication.rescueme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import java.io.FileOutputStream
import java.io.PrintStream
import java.security.MessageDigest
import java.util.*

class PasscodeFragment : Fragment(), View.OnClickListener {
    private var oldPasscodeString = ""
    private var newPasscodeString = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_passcode, container, false)

        val saveBtn = v.findViewById<Button>(R.id.saveBtn)
        saveBtn.setOnClickListener(this)

        // TODO: get edit text value from fragment view.
        val oldPasscode =  v.findViewById<EditText>(R.id.oldPasscodeEditText)
        oldPasscodeString = oldPasscode.text.toString()

        val newPasscode = v.findViewById<EditText>(R.id.newPasscodeEditText)
        newPasscodeString = newPasscode.text.toString()

        return v
    }

    private fun String.toMD5(): String {
        val bytes = MessageDigest.getInstance("MD5").digest(this.toByteArray())
        return bytes.toHex()
    }

    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }

    // compares the entered hashed passcode wih the one saved in file
    private fun isCorrectPasscode(passcodeString: String) : Boolean {
        var originalPasscode = ""
        val hashedOldPasscode = passcodeString.toMD5()
//        Toast.makeText(activity!!, hashedOldPasscode, Toast.LENGTH_LONG).show()
        // figure out why password hashed not same

        val scan = Scanner(activity!!.openFileInput("passcode.txt"))
        while (scan.hasNextLine()) {
            originalPasscode = scan.nextLine()
            Toast.makeText(activity!!, "Old passcode: $oldPasscodeString + Original hashed passcode: + $originalPasscode, Hashed old passcode: + $hashedOldPasscode", Toast.LENGTH_SHORT).show()
        }

        return hashedOldPasscode == originalPasscode
    }

    // overwrite passcode
    private fun overwritePasscode(hashedPasscode : String) {
        val output = PrintStream(activity!!.openFileOutput("passcode.txt", AppCompatActivity.MODE_PRIVATE))
        output.println(hashedPasscode)
        output.close()
    }

    private fun is4digit(passcodeString: String) : Boolean {
        return passcodeString.length == 4
    }

    override fun onClick(v: View?) {
        if (v!!.id == R.id.saveBtn) {
            if (isCorrectPasscode(oldPasscodeString) && is4digit(newPasscodeString)) {
                val hashedNewPasscode = newPasscodeString.toMD5()
                overwritePasscode(hashedNewPasscode)
                Toast.makeText(activity!!, "Change passcode is successful.", Toast.LENGTH_SHORT).show()
            } else {
//                Toast.makeText(activity!!, "Please make sure you entered the correct old passcode and/or a 4-digit new passcode", Toast.LENGTH_LONG).show()
            }
        }
    }

}
