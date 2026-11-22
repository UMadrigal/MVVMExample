package com.macruware.mvvmexample.ui.horoscope

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.macruware.mvvmexample.databinding.FragmentHoroscopeBinding
import com.macruware.mvvmexample.domain.model.HoroscopeInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HoroscopeFragment : Fragment() {

    private var _binding: FragmentHoroscopeBinding? = null
    private val binding get() = _binding!!

    private val horoscopeViewModel by viewModels<HoroscopeViewModel>()

    private lateinit var horoscopeAdapter: HoroscopeAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHoroscopeBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initUi()
    }

    private fun initUi(){
        initHoroscopeList()
        initUiState()
    }

    private fun initHoroscopeList() {
        horoscopeAdapter = HoroscopeAdapter(onItemSelected = { horoscopeSelected ->
            onItemSelected(horoscopeSelected)
        })
        binding.rvHoroscope.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = horoscopeAdapter
        }
    }

    private fun onItemSelected(itemSelected: HoroscopeInfo){
        // temporal
        Toast.makeText(requireContext(), getString(itemSelected.name), Toast.LENGTH_SHORT).show()
    }

    private fun initUiState(){
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED){
                horoscopeViewModel.horoscope.collect { list ->
                    horoscopeAdapter.updateList(list)
                }
            }
        }
    }

}