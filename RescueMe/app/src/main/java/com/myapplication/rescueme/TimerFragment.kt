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
import java.io.PrintStream
import java.util.*

class TimerFragment : Fragment(), View.OnClickListener {
    private lateinit var v : View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        v = inflater.inflate(R.layout.fragment_timer, container, false)

        val saveTimeButton = v.findViewById<Button>(R.id.saveTimeButton)
        saveTimeButton.setOnClickListener(this)

        displayCurrentTime()

        return v
    }

    private fun displayCurrentTime() {
        val currentTimerTextView = v.findViewById<TextView>(R.id.currentTimerTextView)
        if (fileExist("time.txt")) {
            val scan = Scanner(activity!!.openFileInput("time.txt"))

            while (scan.hasNextLine()) {
                val line = scan.nextLine()
                val milliseconds = line.toInt()
                val minutes = milliseconds / 60000
                currentTimerTextView.text = "Current timer is $minutes minutes."
            }
        } else {
            currentTimerTextView.text = "Current Timer is 3 minutes."
        }
    }

    private fun saveTime() {
        val timeEditText = v.findViewById<EditText>(R.id.timeEditText)
        val timeMilliseconds = timeEditText.text.toString().toInt() * 60000

        val output = PrintStream(activity!!.openFileOutput("time.txt", AppCompatActivity.MODE_PRIVATE))
        output.println(timeMilliseconds)
        output.close()

        displayCurrentTime()

        Toast.makeText(activity!!, "Time has been saved.", Toast.LENGTH_SHORT).show()
    }

    private fun fileExist(fname: String?): Boolean {
        val file = activity!!.baseContext.getFileStreamPath(fname)
        return file.exists()
    }

    override fun onClick(v: View?) {
        when (v!!.id) {
            R.id.saveTimeButton -> saveTime()
        }
    }

}