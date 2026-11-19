package com.macruware.mvvmexample.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.macruware.mvvmexample.R
import com.macruware.mvvmexample.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}
