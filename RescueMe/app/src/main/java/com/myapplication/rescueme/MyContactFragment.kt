package com.myapplication.rescueme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_mycontact.*
import org.w3c.dom.Text
import java.io.PrintStream
import java.security.MessageDigest
import java.util.*

class MyContactFragment : Fragment(), View.OnClickListener {
    private lateinit var v: View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        v = inflater.inflate(R.layout.fragment_mycontact, container, false)

        val saveContactBtn = v.findViewById<Button>(R.id.saveContactBtn)
        saveContactBtn.setOnClickListener(this)

        getCurrentContactDetails()

        return v
    }

    private fun getCurrentContactDetails() {
        val currentContactTextView = v.findViewById<TextView>(R.id.currentContactTextView)

        if (!fileExist("my_contact.txt")) {
            currentContactTextView.text = "No contact details."
            return
        }

        var name = ""
        var contactNumber = ""

        val scan = Scanner(activity!!.openFileInput("my_contact.txt"))

        while (scan.hasNextLine()) {
            val line = scan.nextLine()
            val pieces = line.split("\t")

            name = pieces[0]
            contactNumber = pieces[1]

            currentContactTextView.text = "Current contact details:\n$name\n$contactNumber"
        }
    }

    private fun saveContactNumber(name : String, contactNumber : String) {
        val output = PrintStream(activity!!.openFileOutput("my_contact.txt", AppCompatActivity.MODE_PRIVATE))
        output.println(name + "\t" + contactNumber)
        output.close()
    }

    override fun onClick(v: View?) {
        when (v!!.id) {
            R.id.saveContactBtn -> {
                val nameEditText = activity!!.findViewById<EditText>(R.id.nameEditText)
                val name = nameEditText.text.toString()

                val contactEditText = activity!!.findViewById<EditText>(R.id.contactEditText)
                val contactNumber = contactEditText.text.toString()

                if (name.isNotEmpty() && contactNumber.length == 11) {
                    saveContactNumber(name, contactNumber)
                    getCurrentContactDetails()
                    Toast.makeText(activity!!, "Contact details successfully updated.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(activity!!, "Please enter a valid name/contact number.", Toast.LENGTH_SHORT).show()
                }

                nameEditText.text.clear()
                contactEditText.text.clear()
            }
        }
    }

    private fun fileExist(fname: String?): Boolean {
        val file = activity!!.baseContext.getFileStreamPath(fname)
        return file.exists()
    }

}
