package com.myapplication.rescueme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import java.io.PrintStream
import java.security.MessageDigest
import java.util.*

class PasscodeFragment : Fragment(), View.OnClickListener {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_passcode, container, false)

        val saveBtn = v.findViewById<Button>(R.id.saveBtn)
        saveBtn.setOnClickListener(this)

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
        val oldPasscode =  activity!!.findViewById<EditText>(R.id.oldPasscodeEditText)
        val oldPasscodeString = oldPasscode.text.toString()

        val newPasscode = activity!!.findViewById<EditText>(R.id.newPasscodeEditText)
        val newPasscodeString = newPasscode.text.toString()

        if (v!!.id == R.id.saveBtn) {
            if (isCorrectPasscode(oldPasscodeString) && is4digit(newPasscodeString)) {
                val hashedNewPasscode = newPasscodeString.toMD5()
                overwritePasscode(hashedNewPasscode)
                Toast.makeText(activity!!, "Change passcode is successful.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity!!, "Please make sure you entered the correct old passcode and/or a 4-digit new passcode", Toast.LENGTH_LONG).show()
            }
        }
    }

}
