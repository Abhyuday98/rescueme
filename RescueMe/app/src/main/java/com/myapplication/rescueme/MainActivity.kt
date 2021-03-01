package com.myapplication.rescueme

import android.R
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.myapplication.rescueme.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {
    private lateinit var binding:ActivityMainBinding
    private val PICK_CONTACT = 1

    private var contactToDetails = HashMap<String, ArrayList<String>>()
    private var details = ArrayList<String>()
    private lateinit var myAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    fun goToContacts(view: View) {
        val it = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
        startActivityForResult(it, PICK_CONTACT);
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_CONTACT && resultCode == RESULT_OK && data != null) {
            val contactData = data.data!!
            val cursor1 = contentResolver.query(contactData, null, null, null, null)

            if (cursor1 != null && cursor1.moveToFirst()) {
                val contactId = cursor1.getString(cursor1.getColumnIndex(ContactsContract.Contacts._ID))
                val contactName = cursor1.getString(cursor1.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
                val idResults = cursor1.getString(cursor1.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)).toInt()
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
                        Toast.makeText(this, "Name: $contactName, PhoneNumber: $contactNumber", Toast.LENGTH_LONG).show()
                    }
                    cursor2.close()

                    // details will be [name, number1, number2, ...]
                    contactToDetails[contactId] = details
                }

                // TODO: Display name and contactNumber as one list item. Look into custom adapters.
                myAdapter = ArrayAdapter(this, R.layout.simple_list_item_2, R.id.text1, details)
                binding.contactsLv.adapter = myAdapter

                cursor1.close()
            }
        }

    }
}