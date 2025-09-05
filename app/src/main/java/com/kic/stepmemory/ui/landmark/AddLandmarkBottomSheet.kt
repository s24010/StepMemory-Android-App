package com.kic.stepmemory.ui.landmark

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ActivityAddLandmarkBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.chipPin.isChecked = true

        binding.btnSaveLandmark.setOnClickListener {
            val title = binding.etLandmarkTitle.text.toString().trim()
            if (title.isEmpty()) {
                Toast.makeText(context, "タイトルを入力してください。", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val episode = binding.etLandmarkEpisode.text.toString().trim()
            val iconType = getSelectedIconType()

            val landmark = Landmark(
                title = title,
                episode = episode,
                iconType = iconType,
                latitude = latitude,
                longitude = longitude,
                // ★★★ System.currentTimeMillis() から Date() に変更 ★★★
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