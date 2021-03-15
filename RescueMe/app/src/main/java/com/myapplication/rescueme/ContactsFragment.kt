package com.myapplication.rescueme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.myapplication.rescueme.databinding.ActivityMainBinding
import java.io.PrintStream
import java.util.*
import kotlin.collections.ArrayList

class ContactsFragment : Fragment(), View.OnClickListener {
    private val PICK_CONTACT = 1
    private lateinit var myAdapter: ArrayAdapter<String>
    private var contactsList = ArrayList<String>()
    private var contactObjectsList = ArrayList<Contact>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreate(savedInstanceState)
        val v = inflater.inflate(R.layout.fragment_contacts, container, false)

        setupContactsList()
        // have a list of Contacts
        // define myAdapter with ContactAdapter
        // contactsListView.adapater = myAdapter

//        myAdapter = ArrayAdapter(activity!!, android.R.layout.simple_list_item_1, contactsList)
//        val contactsListView = v.findViewById<ListView>(R.id.contactsListView)
//        contactsListView.adapter = myAdapter

        // Remove chosen contact on click
//        contactsListView.setOnItemClickListener {list, _, index, _ ->

//        }

        val addContactButton = v.findViewById<Button>(R.id.addContactButton)
        addContactButton.setOnClickListener(this)

        return v
    }

    override fun onClick(v: View?) {
        when (v!!.id) {
            R.id.addContactButton -> goToContacts()
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
                        val contactNumber = cursor2.getString(cursor2.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                        details.add(contactNumber)
                        Toast.makeText(activity!!, "Name: $contactName, PhoneNumber: $contactNumber", Toast.LENGTH_LONG).show()
                    }
                    cursor2.close()
                }
                cursor1.close()
                writeFile(details)
            }
        }
    }

    private fun writeFile(details : ArrayList<String>) {
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

//            // append pieces as a string in this format "Name\nContactNumber"
//            val contactDetails = "$contactName\n$contactNumber"
//            if (!contactsList.contains(contactDetails)) {
//                contactsList.add(contactDetails)
//            }

            // Create list of contact objects
            val contactObject = Contact(contactId, contactName, contactNumber)
            if (!contactObjectsList.contains(contactObject)) {
                contactObjectsList.add(contactObject)
            }
        }
    }


}