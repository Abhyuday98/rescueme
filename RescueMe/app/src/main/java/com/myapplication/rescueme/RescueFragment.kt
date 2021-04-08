package com.myapplication.rescueme

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.util.*
import kotlin.collections.HashMap

class RescueFragment : Fragment() {
    private lateinit var v : View
    private var myNum: String = ""
    private var myName: String = ""
    private var nameList = arrayListOf<String>()
    private var detailList = arrayListOf<HashMap<*,*>>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity!!.title = "Rescue Who"

        activity?.onBackPressedDispatcher?.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
            }
        })

        v = inflater.inflate(R.layout.fragment_rescue, container, false)

        readFromDB()
        v.findViewById<ListView>(R.id.helpLV).setOnItemClickListener{ list, _, index, _ ->
            var selected = detailList.get(index)
            var name = selected["VictimName"].toString()
            var victimNum = selected["VictimNum"].toString()
            var RescuerNum = selected["RescuerNum"].toString()

            val detailsIt = Intent(activity, RescueDetailsActivity::class.java)
            detailsIt.putExtra("victimName", name)
            detailsIt.putExtra("victimNum", victimNum)
            detailsIt.putExtra("rescuerNum", RescuerNum)

            startActivity(detailsIt)
        }
        return v
    }

    private fun readFromDB() {
        val database = Firebase.database
        val myRef = database.getReference("RescueRecords")

        //read records
        myRef.get().addOnSuccessListener {
            var data = it.value
            val scan = Scanner(activity!!.openFileInput("my_contact.txt"))
            while(scan.hasNextLine()) {
                var userDet = scan.nextLine().split("\t")
                myName = userDet[0]
                myNum = userDet[1]
            }

            myNum = "+65 9850 9431"
            var isNeedHelp = false
            Log.i("firebase", "Got value ${it.value}")
            if (data != null && data is HashMap<*,*>) {
                for (victim in data.keys) {
                    var record = data[victim]
                    if (record is HashMap<*,*>) {
                        var contacts = record.keys
                        if (myNum in contacts) {
                            var details = record[myNum]
                            if (details is HashMap<*,*>) {
                                Log.i("record", "record is ${details["VictimName"]}")
                                nameList.add(details["VictimName"].toString())
                                detailList.add(details)
                                isNeedHelp = true
                            }
                        }

                    }
                }
                if (!isNeedHelp) {
                    Toast.makeText(activity, "All is well! There is no call for help.", Toast.LENGTH_LONG).show()
                    v.findViewById<TextView>(R.id.instructionsTV).text = "All is well!"
                } else {
                    var myAdapter = ArrayAdapter<String>(activity!!, android.R.layout.simple_list_item_1, nameList)
                    v.findViewById<ListView>(R.id.helpLV).adapter = myAdapter
                }
            } else {
                Toast.makeText(activity, "Error getting data this", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener{
            Log.e("firebase", "Error getting data", it)
        }
    }

}