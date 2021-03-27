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

class MyContactFragment : Fragment(), View.OnClickListener {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_mycontact, container, false)

        val saveContactBtn = v.findViewById<Button>(R.id.saveContactBtn)
        saveContactBtn.setOnClickListener(this)

        return v
    }

    private fun saveContactNumber(contactNumber : String) {
        val output = PrintStream(activity!!.openFileOutput("my_contact.txt", AppCompatActivity.MODE_PRIVATE))
        output.println(contactNumber)
        output.close()
    }

    private fun isCorrectContact(contactNumber: String) : Boolean {
        var currentContactNumber = ""

        val scan = Scanner(activity!!.openFileInput("my_contact.txt"))
        while (scan.hasNextLine()) {
            currentContactNumber = scan.nextLine()
        }

        return currentContactNumber == contactNumber
    }

    override fun onClick(v: View?) {
        val oldContactEditText =  activity!!.findViewById<EditText>(R.id.oldContactEditText)
        val oldContact = oldContactEditText.text.toString()

        val newContactEditText = activity!!.findViewById<EditText>(R.id.newContactEditText)
        val newContact = newContactEditText.text.toString()

        val confirmNewContactEditText = activity!!.findViewById<EditText>(R.id.confirmNewContactEditText)
        val confirmNewContact = confirmNewContactEditText.text.toString()

        var errorMsg = ""
        if (v!!.id == R.id.saveContactBtn) {
            if (!isCorrectContact(oldContact)) {
                errorMsg += "Old contact entered is incorrect.\n"
            }

            if (newContact.length != 11) {
                errorMsg += "Please enter a valid new contact.\n"
            }

            if(newContact != confirmNewContact) {
                errorMsg += "Confirm new contact does not match.\n"
            }

            if (isCorrectContact(oldContact) && newContact.length == 11 && newContact == confirmNewContact) {
                saveContactNumber(newContact)
                Toast.makeText(activity!!, "Change my contact is successful.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity!!, errorMsg.dropLast(2), Toast.LENGTH_LONG).show()
            }
        }
    }

}
