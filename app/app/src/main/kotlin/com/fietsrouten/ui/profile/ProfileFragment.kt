package com.fietsrouten.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import com.fietsrouten.AppPreferences
import com.fietsrouten.R
import com.fietsrouten.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupLanguageToggle()
        setupThemeToggle()
        setupVoiceGuidanceSwitch()
    }

    private fun activeLanguageIsDutch(): Boolean {
        val override = AppCompatDelegate.getApplicationLocales().get(0)?.language
        val language = override ?: resources.configuration.locales[0].language
        return language == "nl"
    }

    private fun setupLanguageToggle() {
        binding.languageToggle.check(if (activeLanguageIsDutch()) R.id.btnLangNl else R.id.btnLangEn)
        binding.languageToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val tag = if (checkedId == R.id.btnLangNl) "nl" else "en"
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }

    private fun setupThemeToggle() {
        val id = when (AppPreferences.getThemeMode(requireContext())) {
            AppPreferences.ThemeMode.LIGHT -> R.id.btnThemeLight
            AppPreferences.ThemeMode.DARK -> R.id.btnThemeDark
            AppPreferences.ThemeMode.SYSTEM -> R.id.btnThemeSystem
        }
        binding.themeToggle.check(id)
        binding.themeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.btnThemeLight -> AppPreferences.ThemeMode.LIGHT
                R.id.btnThemeDark -> AppPreferences.ThemeMode.DARK
                else -> AppPreferences.ThemeMode.SYSTEM
            }
            AppPreferences.setThemeMode(requireContext(), mode)
        }
    }

    private fun setupVoiceGuidanceSwitch() {
        binding.switchVoiceGuidance.isChecked = AppPreferences.isVoiceGuidanceEnabled(requireContext())
        binding.switchVoiceGuidance.setOnCheckedChangeListener { _, checked ->
            AppPreferences.setVoiceGuidanceEnabled(requireContext(), checked)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
