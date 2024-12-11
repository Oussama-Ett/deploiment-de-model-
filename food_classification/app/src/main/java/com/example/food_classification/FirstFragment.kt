package com.example.food_classification

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.food_classification.databinding.FragmentFirstBinding

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Délai de 2 secondes avant de naviguer vers la deuxième page
        Handler().postDelayed({
            // Navigation vers le second fragment après 2 secondes
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }, 2000)  // 2000 millisecondes = 2 secondes
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
