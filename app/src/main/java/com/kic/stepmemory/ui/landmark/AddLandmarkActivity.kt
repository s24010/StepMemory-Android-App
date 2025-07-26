package com.kic.stepmemory.ui.landmark

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.kic.stepmemory.data.Landmark
import com.kic.stepmemory.databinding.ActivityAddLandmarkBinding

class AddLandmarkActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddLandmarkBinding
    private lateinit var firestore: FirebaseFirestore
    private var location: GeoPoint? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddLandmarkBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "思い出の場所を登録"

        val lat = intent.getDoubleExtra("LATITUDE", 0.0)
        val lng = intent.getDoubleExtra("LONGITUDE", 0.0)
        if (lat != 0.0 && lng != 0.0) {
            location = GeoPoint(lat, lng)
        }

        binding.btnSaveLandmark.setOnClickListener {
            saveLandmarkToFirestore()
        }
    }

    private fun saveLandmarkToFirestore() {
        val landmarkName = binding.etLandmarkName.text.toString().trim()
        val episodeContent = binding.etEpisodeContent.text.toString().trim()

        if (landmarkName.isEmpty()) {
            Toast.makeText(this, "思い出のタイトルを入力してください。", Toast.LENGTH_SHORT).show()
            return
        }

        if (location == null) {
            Toast.makeText(this, "場所の位置情報がありません。", Toast.LENGTH_SHORT).show()
            return
        }

        val newLandmark = Landmark(
            name = landmarkName,
            episode = episodeContent,
            location = location!!,
            createdAt = java.util.Date()
        )

        firestore.collection("landmarks")
            .add(newLandmark)
            .addOnSuccessListener {
                Toast.makeText(this, "ランドマークを登録しました！", Toast.LENGTH_SHORT).show()
                finish() // 登録が完了したら画面を閉じる
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "登録に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}