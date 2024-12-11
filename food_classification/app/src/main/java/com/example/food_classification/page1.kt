package com.example.food_classification

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class page1 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_page1)

        // Configure les marges pour tenir compte des barres système
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Attendre 2 secondes avant de passer à Page2
        Handler(Looper.getMainLooper()).postDelayed({
            // Démarrer l'activité Page2
            val intent = Intent(this, page2::class.java)
            startActivity(intent)
            finish() // Fermer Page1 pour que l'utilisateur ne puisse pas revenir en arrière
        }, 2000) // 2000 ms = 2 secondes
    }
}
