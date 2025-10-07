package com.kic.stepmemory.ui.landmark

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.kic.stepmemory.BuildConfig
import com.kic.stepmemory.challenge.UnlockedIconManager
import com.kic.stepmemory.data.Landmark
import com.kic.stepmemory.databinding.ActivityAddLandmarkBottomSheetBinding
import java.util.Date

class AddLandmarkBottomSheet(
    private val latitude: Double,
    private val longitude: Double,
    private val onSave: (Landmark) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: ActivityAddLandmarkBottomSheetBinding? = null
    private val binding get() = _binding!!
    private lateinit var unlockedIconManager: UnlockedIconManager
    private lateinit var auth: FirebaseAuth
    private var userId: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ActivityAddLandmarkBottomSheetBinding.inflate(inflater, container, false)
        
        auth = FirebaseAuth.getInstance()
        userId = auth.currentUser?.uid

        if (userId == null) {
            Toast.makeText(requireContext(), "ログインが必要です。", Toast.LENGTH_SHORT).show()
            dismiss()
        } else {
            unlockedIconManager = UnlockedIconManager(requireContext(), userId!!)
        }
        
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        if (userId == null) return

        binding.chipPin.isChecked = true

        // アンロック状態に応じて特別なアイコンの表示を切り替え
        if (BuildConfig.ALL_FEATURES_UNLOCKED || unlockedIconManager.isIconUnlocked("bronze_pin")) {
            binding.chipBronzePin.visibility = View.VISIBLE
        }
        if (BuildConfig.ALL_FEATURES_UNLOCKED || unlockedIconManager.isIconUnlocked("silver_pin")) {
            binding.chipSilverPin.visibility = View.VISIBLE
        }
        if (BuildConfig.ALL_FEATURES_UNLOCKED || unlockedIconManager.isIconUnlocked("gold_pin")) {
            binding.chipGoldPin.visibility = View.VISIBLE
        }
        if (BuildConfig.ALL_FEATURES_UNLOCKED || unlockedIconManager.isIconUnlocked("moon_icon")) {
            binding.chipMoonIcon.visibility = View.VISIBLE
        }

        binding.btnSaveLandmark.setOnClickListener {
            val title = binding.etLandmarkTitle.text.toString().trim()
            if (title.isEmpty()) {
                Toast.makeText(context, "タイトルを入力してください。", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val episode = binding.etLandmarkEpisode.text.toString().trim()
            val iconType = getSelectedIconType()

            val landmark = Landmark(
                userId = userId!!, // ユーザーIDを追加
                title = title,
                episode = episode,
                iconType = iconType,
                latitude = latitude,
                longitude = longitude,
                createdAt = Date()
            )
            onSave(landmark)
            dismiss()
        }
    }

    private fun getSelectedIconType(): String {
        return when (binding.chipGroupIcons.checkedChipId) {
            binding.chipFood.id -> "FOOD"
            binding.chipScenery.id -> "SCENERY"
            binding.chipOnsen.id -> "ONSEN"
            binding.chipShopping.id -> "SHOPPING"
            binding.chipSightseeing.id -> "SIGHTSEEING"

            binding.chipBronzePin.id -> "BRONZE_PIN"
            binding.chipSilverPin.id -> "SILVER_PIN"
            binding.chipGoldPin.id -> "GOLD_PIN"
            binding.chipMoonIcon.id -> "MOON_ICON"

            else -> "PIN"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AddLandmarkBottomSheet"
    }
}
