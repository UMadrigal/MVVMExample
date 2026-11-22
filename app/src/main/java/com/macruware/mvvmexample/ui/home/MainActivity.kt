package com.macruware.mvvmexample.ui.home

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.get
import androidx.core.view.size
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.macruware.mvvmexample.R
import com.macruware.mvvmexample.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var lastSelectedId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initUI()
    }

    private fun initUI() {
        binding.bottomNavView.post {
            disableNavClipping()
            animateNavIcons(binding.bottomNavView.selectedItemId)
        }
        initNavigation()
    }

    private fun initNavigation() {
        val navHost = supportFragmentManager.findFragmentById(R.id.fragmentContainerView) as NavHostFragment
        navController = navHost.navController
        navController.addOnDestinationChangedListener { _, destination, _ ->
            animateNavIcons(destination.id)
        }
        binding.bottomNavView.setupWithNavController(navController)
    }

    private fun animateNavIcons(selectedId: Int) {
        val isFirstCall = lastSelectedId == -1
        val selectionChanged = selectedId != lastSelectedId
        lastSelectedId = selectedId

        val menu = binding.bottomNavView.menu
        for (i in 0 until menu.size) {
            val item = menu[i]
            val itemView = binding.bottomNavView.findViewById<View>(item.itemId) ?: continue
            val icon = itemView.findViewById<View>(
                com.google.android.material.R.id.navigation_bar_item_icon_view
            ) ?: continue

            val isSelected = item.itemId == selectedId
            val scale = if (isSelected) 1.2f else 1f

            icon.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(150)
                .start()

            if (isSelected && selectionChanged && !isFirstCall) {
                rotateIcon(icon)
            }
        }
    }

    // Al escalar los íconos se veían cortados, esta función lo soluciona
    private fun disableNavClipping() {
        val nav = binding.bottomNavView
        for (i in 0 until nav.menu.size) {
            val itemView = nav.findViewById<View>(nav.menu[i].itemId) as? ViewGroup ?: continue
            itemView.clipChildren = false
            itemView.clipToPadding = false
        }
    }

    private fun rotateIcon(icon: View) {
        ObjectAnimator.ofFloat(icon, View.ROTATION, 0f, 360f)
            .setDuration(400)
            .start()
    }
}
