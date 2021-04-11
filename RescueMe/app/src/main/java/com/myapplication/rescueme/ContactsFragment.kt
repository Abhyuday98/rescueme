package com.myapplication.rescueme

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import java.io.File
import java.io.PrintStream
import java.util.*
import kotlin.collections.ArrayList


class ContactsFragment : Fragment(), View.OnClickListener {
    private val PICK_CONTACT = 1

    private lateinit var myAdapter: ContactAdapter
    private var contactsList = ArrayList<Contact>()

    private lateinit var v: View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity!!.title = "Contacts"

        activity?.onBackPressedDispatcher?.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (contactsList.size < 3) {
                    showAlertDialog()
                }
            }
        })

        v = inflater.inflate(R.layout.fragment_contacts, container, false)
        setupContactsList()

        // temporarily just remove based on item click
        val contactsListView = v.findViewById<ListView>(R.id.contactsListView)
        contactsListView.setOnItemClickListener { list, _, index, _ ->
            contactsList.removeAt(index)
            myAdapter.notifyDataSetChanged()

            rewrite() // rewrite the file with new contactsList
        }

        val addContactButton = v.findViewById<Button>(R.id.addContactButton)
        addContactButton.setOnClickListener(this)

        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupContactsList()
    }

    private fun rewrite() {
        // If contactsList is empty, delete the file.
        if (contactsList.size == 0) {
            val dir = activity!!.filesDir
            val file = File(dir, "contacts.txt")
            file.delete()
        } else {
            for (i in 0 until contactsList.size) {
                val contactId = contactsList[i].id
                val contactName = contactsList[i].name
                val contactNumber = contactsList[i].number

                val details = contactId + "\t" + contactName + "\t" + contactNumber

                if (i == 0) {
                    val output = PrintStream(activity!!.openFileOutput("contacts.txt", AppCompatActivity.MODE_PRIVATE))
                    output.println(details)
                    output.close()
                } else {
                    val output = PrintStream(activity!!.openFileOutput("contacts.txt", AppCompatActivity.MODE_APPEND))
                    output.println(details)
                    output.close()
                }
            }
        }
    }

    fun goToContacts() {
        if (hasReadContactsPermission()) {
            pickContact()
        } else {
            requestReadContactsPermission()
        }
    }

    private fun pickContact() {
        val it = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
        startActivityForResult(it, PICK_CONTACT);
    }

    private fun hasReadContactsPermission() : Boolean {
        return (ActivityCompat.checkSelfPermission(activity!!, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }

    private fun requestReadContactsPermission() {
        ActivityCompat.requestPermissions(activity!!, arrayOf(Manifest.permission.READ_CONTACTS), 111)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 111 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pickContact()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_CONTACT && resultCode == AppCompatActivity.RESULT_OK && data != null) {
            val contactData = data.data!!
            val cursor1 = activity!!.contentResolver.query(contactData, null, null, null, null)
            var details = ArrayList<String>()

            if (cursor1 != null && cursor1.moveToFirst()) {
                val contactId = cursor1.getString(cursor1.getColumnIndex(ContactsContract.Contacts._ID))
                val contactName = cursor1.getString(cursor1.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
                val idResults = cursor1.getString(cursor1.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)).toInt()
                details.add(contactId)
                details.add(contactName)

                // checks if contact has phone number
                if (idResults == 1) {
                    val cursor2 = activity!!.contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = " + contactId,
                            null,
                            null)

                    // a contact may have multiple phone numbers
                    while (cursor2!!.moveToNext()) {
                        // get phone number
                        var contactNumber = cursor2.getString(cursor2.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                        contactNumber = formatContactNumber(contactNumber)
                        details.add(contactNumber)
                        Toast.makeText(activity!!, "Name: $contactName, PhoneNumber: $contactNumber", Toast.LENGTH_LONG).show()
                    }
                    cursor2.close()
                    cursor1.close()
                    writeFile(details)
                } else {
                    Toast.makeText(activity!!, "This contact does not have a number. Please choose another contact!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun writeFile(details: ArrayList<String>) {
        var contactExists = false

        // check if file exist before reading.
        if (fileExist("contacts.txt")) {
            val scan = Scanner(activity!!.openFileInput("contacts.txt"))

            while (scan.hasNextLine()) {
                val line = scan.nextLine()
                val contactDetails = line.split("\t")

                // check if contactId in file. If yes, stop reading.
                if (contactDetails[0] == details[0]) {
                    contactExists = true
                    break
                }
            }
        }

        // Only write if contact does not exist.
        if (!contactExists) {
            val output = PrintStream(activity!!.openFileOutput("contacts.txt", AppCompatActivity.MODE_APPEND))
            output.println(details.joinToString(separator = "\t"))
            output.close()
            setupContactsList()
            Toast.makeText(activity!!, "Contact added successfully!", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(activity!!, "This contact has already been chosen!", Toast.LENGTH_LONG).show()
        }
    }

    private fun fileExist(fname: String?): Boolean {
        val file = activity!!.baseContext.getFileStreamPath(fname)
        return file.exists()
    }

    private fun setupContactsList() {
        if (!fileExist("contacts.txt")) {
            return
        }

        val scan = Scanner(activity!!.openFileInput("contacts.txt"))
        while (scan.hasNextLine()) {
            val line = scan.nextLine()
            val pieces = line.split("\t")
            val contactId = pieces[0]
            val contactName = pieces[1]
            val contactNumber = pieces[2]

            // create list of contact objects
            val contact = Contact(contactId, contactName, contactNumber)
            if (!contactsList.contains(contact)) {
                contactsList.add(contact)
            }

        }

        myAdapter = ContactAdapter(activity!!, contactsList)

        val contactsListView = v.findViewById<ListView>(R.id.contactsListView)
        contactsListView.adapter = myAdapter
    }

    // join any spaces, add +65 in front if no prefix starting with +.
    private fun formatContactNumber(contactNumber : String) : String {
        var result = ""

        if (contactNumber.substring(0, 1) != "+") {
            result = "+65$contactNumber"
        } else {
            result = contactNumber
        }

        result = result.split(" ").joinToString("")
        return result
    }

    private fun showAlertDialog() {
        val builder = AlertDialog.Builder(activity!!)
        builder.setTitle("Insufficient contacts")
        builder.setMessage("Please choose at least 3 contacts.")

        builder.setNeutralButton("Ok") { dialog, which ->
        }

        builder.show()
    }

    override fun onClick(v: View?) {
        when (v!!.id) {
            R.id.addContactButton -> goToContacts()
        }
    }

}