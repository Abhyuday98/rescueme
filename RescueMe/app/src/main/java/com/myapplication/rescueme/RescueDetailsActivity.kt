package com.myapplication.rescueme

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RescueDetailsActivity : AppCompatActivity() {
    private var victimName = ""
    private var victimNum = ""
    private var rescuerNum = ""
    private var url = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rescue_details)

        val extras = intent.extras
        if (extras != null) {
            victimName = extras.getString("victimName")!!
            victimNum = extras.getString("victimNum")!!
            rescuerNum = extras.getString("rescuerNum")!!
        }

        val victimNameTv = findViewById<TextView>(R.id.victimNameTv)
        victimNameTv.text = "Victim Name: $victimName"

        val victimNumberTv = findViewById<TextView>(R.id.victimNumberTv)
        victimNumberTv.text = "Victim Number: $victimNum"

        getVideo()
    }

    fun watchVideo(view: View) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(i)
    }

    fun goToRescueActivity(view: View) {
        val it = Intent(this, RescueActivity::class.java)
        it.putExtra("victimName", victimName)
        it.putExtra("victimNum", victimNum)
        it.putExtra("rescuerNum", rescuerNum)
        startActivity(it)
    }


    // Gets video url. Show videoBtn if url exist.
    private fun getVideo() {
        if (victimNum == "") {
            Toast.makeText(this, "There is no victim number.", Toast.LENGTH_SHORT).show()
            return
        }


        val storageRef = FirebaseStorage.getInstance().reference
        val videoBtn = findViewById<Button>(R.id.videoBtn)

        storageRef.child(victimNum).downloadUrl.addOnSuccessListener {
            url = it.toString()
            videoBtn.visibility = View.VISIBLE
            Log.i("it download url", "$it")
        }.addOnFailureListener {
            videoBtn.visibility = View.GONE
            Log.i("it exception", "${it.message}")
        }
    }
}