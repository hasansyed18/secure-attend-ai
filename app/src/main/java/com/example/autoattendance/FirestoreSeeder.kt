package com.example.autoattendance

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

object FirestoreSeeder {
    private val db = FirebaseFirestore.getInstance()

    fun seedIfNeeded() {
        db.collection("states").limit(1).get().addOnSuccessListener { snapshot ->
            if (snapshot.isEmpty) {
                Log.d("FirestoreSeeder", "States collection empty. Starting seeding...")
                seedData()
            } else {
                Log.d("FirestoreSeeder", "Firestore already has data. Skipping seed.")
            }
        }
    }

    private fun seedData() {
        // 1. Seed States
        val states = listOf(
            mapOf("stateId" to "KA", "name" to "Karnataka"),
            mapOf("stateId" to "TS", "name" to "Telangana"),
            mapOf("stateId" to "MH", "name" to "Maharashtra")
        )

        states.forEach { state ->
            db.collection("states").document(state["stateId"] as String).set(state)
        }

        // 2. Seed Cities
        val cities = listOf(
            mapOf("cityId" to "BDR", "name" to "Bidar", "stateId" to "KA"),
            mapOf("cityId" to "KLB", "name" to "Kalaburagi", "stateId" to "KA"),
            mapOf("cityId" to "BLR", "name" to "Bengaluru", "stateId" to "KA"),
            mapOf("cityId" to "HYD", "name" to "Hyderabad", "stateId" to "TS")
        )

        cities.forEach { city ->
            db.collection("cities").document(city["cityId"] as String).set(city)
        }

        // 3. Seed Institutions
        val institutions = listOf(
            mapOf(
                "institutionId" to "LAEC001",
                "name" to "Lingaraj Appa Engineering College",
                "stateId" to "KA",
                "cityId" to "BDR",
                "status" to "verified",
                "subscriptionPlan" to "none",
                "createdAt" to com.google.firebase.Timestamp.now()
            ),
            mapOf(
                "institutionId" to "GNDEC001",
                "name" to "Guru Nanak Dev Engineering College",
                "stateId" to "KA",
                "cityId" to "BDR",
                "status" to "verified",
                "subscriptionPlan" to "none",
                "createdAt" to com.google.firebase.Timestamp.now()
            ),
            mapOf(
                "institutionId" to "GECB001",
                "name" to "Government Engineering College Bidar",
                "stateId" to "KA",
                "cityId" to "BDR",
                "status" to "verified",
                "subscriptionPlan" to "none",
                "createdAt" to com.google.firebase.Timestamp.now()
            )
        )

        institutions.forEach { inst ->
            db.collection("institutions").document(inst["institutionId"] as String).set(inst)
        }
        
        Log.d("FirestoreSeeder", "Seeding completed successfully.")
    }
}
