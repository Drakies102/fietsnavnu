package com.fietsrouten.ui.rides

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.fietsrouten.data.repository.RidesStore
import com.fietsrouten.databinding.FragmentRidesBinding

class RidesFragment : Fragment() {

    private var _binding: FragmentRidesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRidesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val rides = RidesStore.getRides(requireContext())
        binding.tvRidesEmpty.visibility = if (rides.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerRides.visibility = if (rides.isEmpty()) View.GONE else View.VISIBLE
        binding.recyclerRides.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerRides.adapter = RidesAdapter(rides)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
