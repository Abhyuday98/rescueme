package com.myapplication.rescueme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.myapplication.rescueme.databinding.ActivityMainBinding
import java.io.File
import java.io.PrintStream
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() {
    private lateinit var binding:ActivityMainBinding
    private val PICK_CONTACT = 1

    private lateinit var myAdapter: ContactAdapter
    private var contactsList = ArrayList<Contact>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Service START
        intent = Intent(this, MyService::class.java)
        startService(intent)
        //
        setupContactsList()

        // temporarily just remove based on item click
        binding.contactsLv.setOnItemClickListener { list, _, index, _ ->
            contactsList.removeAt(index)
            myAdapter.notifyDataSetChanged()

            rewrite() // rewrite the file with new contactsList
        }

        // go directly to HomeActivity if passcode exists
        if (fileExist("passcode.txt")) {
            val it = Intent(this, HomeActivity::class.java)
            startActivity(it)
        }
    }

    private fun rewrite() {
        // If contactsList is empty, delete the file.
        if (contactsList.size == 0) {
            val dir = filesDir
            val file = File(dir, "contacts.txt")
            file.delete()
        } else {
            for (i in 0 until contactsList.size) {
                val contactId = contactsList[i].id
                val contactName = contactsList[i].name
                val contactNumber = contactsList[i].number

                val details = contactId + "\t" + contactName + "\t" + contactNumber

                if (i == 0) {
                    val output = PrintStream(openFileOutput("contacts.txt", MODE_PRIVATE))
                    output.println(details)
                    output.close()
                } else {
                    val output = PrintStream(openFileOutput("contacts.txt", MODE_APPEND))
                    output.println(details)
                    output.close()
                }
            }
        }
    }

    fun goToContacts(view: View) {
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
        return (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }

    private fun requestReadContactsPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), 111)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 111 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pickContact()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_CONTACT && resultCode == RESULT_OK && data != null) {
            val contactData = data.data!!
            val cursor1 = contentResolver.query(contactData, null, null, null, null)
            var details = ArrayList<String>()

            if (cursor1 != null && cursor1.moveToFirst()) {
                val contactId = cursor1.getString(cursor1.getColumnIndex(ContactsContract.Contacts._ID))
                val contactName = cursor1.getString(cursor1.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
                val idResults = cursor1.getString(cursor1.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)).toInt()
                details.add(contactId)
                details.add(contactName)

                // checks if contact has phone number
                if (idResults == 1) {
                    val cursor2 = contentResolver.query(
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
                    }
                    cursor2.close()
                    cursor1.close()
                    writeFile(details)
                } else {
                    Toast.makeText(this, "This contact does not have a number. Please choose another contact!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun writeFile(details: ArrayList<String>) {
        var contactExists = false

        // check if file exist before reading.
        if (fileExist("contacts.txt")) {
            val scan = Scanner(openFileInput("contacts.txt"))

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
            val output = PrintStream(openFileOutput("contacts.txt", MODE_APPEND))
            output.println(details.joinToString(separator = "\t"))
            output.close()
            setupContactsList()
            Toast.makeText(this, "Contact added successfully!", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "This contact has already been chosen!", Toast.LENGTH_LONG).show()
        }
    }

    private fun fileExist(fname: String?): Boolean {
        val file = baseContext.getFileStreamPath(fname)
        return file.exists()
    }

    private fun setupContactsList() {
        if (!fileExist("contacts.txt")) {
            return
        }

        val scan = Scanner(openFileInput("contacts.txt"))
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

        myAdapter = ContactAdapter(this, contactsList)
        binding.contactsLv.adapter = myAdapter

        showButton()
    }

    fun goToUserContactActivity(view: View) {
        val it = Intent(this, UserContactActivity::class.java)
        startActivity(it)
    }

    // show button only if contacts are minimally 3
    private fun showButton() {
        if (contactsList.size >= 3) {
            binding.nextBtn.visibility = View.VISIBLE
        }
    }
}