package com.myapplication.rescueme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Camera
import android.media.MediaRecorder
import android.opengl.Visibility
import android.os.Bundle
import android.os.Handler
import android.view.MenuItem
import android.view.SurfaceView
import android.view.View
import android.widget.Toast
import android.widget.Toolbar
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.myapplication.rescueme.databinding.ActivityHomeBinding

class HomeActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var binding:ActivityHomeBinding
    private lateinit var drawer : DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = binding.toolbar
        setSupportActionBar(toolbar)

        drawer = binding.drawerLayout
        val navigationView : NavigationView = findViewById(R.id.nav_view) //somehow binding cannot find that id nav_view
        navigationView.setNavigationItemSelectedListener(this)

        val toggle : ActionBarDrawerToggle = ActionBarDrawerToggle(this, drawer, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer.addDrawerListener(toggle)
        toggle.syncState()

        // savedInstanceState null condition means load Home fragment on create if no rotation
        // E.g. if contacts fragment is open, screen rotates stays at contacts fragment, will not load home fragment.
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.fragment_container, HomeFragment()).commit()
            navigationView.setCheckedItem(R.id.home)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.home -> {
                supportFragmentManager.beginTransaction().replace(R.id.fragment_container, HomeFragment()).commit()
            }
            R.id.contacts_setting -> {
                supportFragmentManager.beginTransaction().replace(R.id.fragment_container, ContactsFragment()).commit()
            }
            R.id.timer_setting -> {
                supportFragmentManager.beginTransaction().replace(R.id.fragment_container, TimerFragment()).commit()
            }
            R.id.passcode_setting -> {
                supportFragmentManager.beginTransaction().replace(R.id.fragment_container, PasscodeFragment()).commit()
            }
            R.id.rescue_tab -> {
                supportFragmentManager.beginTransaction().replace(R.id.fragment_container, RescueFragment()).commit()
            }
            R.id.mycontact_setting -> {
                supportFragmentManager.beginTransaction().replace(R.id.fragment_container, MyContactFragment()).commit()
            }
        }

        drawer.closeDrawer(GravityCompat.START)
        return true
    }

    // so that when user press back, close navigation drawer if open instead of exiting activity first
    override fun onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}