package com.myapplication.rescueme

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.myapplication.rescueme.databinding.ActivityHomeBinding

class HomeActivity : AppCompatActivity() {
    private lateinit var binding:ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    // TODO: trigger video recording when help button is pressed (or when pick up sounds of distressed (later))
    fun sendHelp(view: View) {}
}