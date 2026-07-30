package com.example.autoattendance.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.example.autoattendance.R

/**
 * BoundarySetupActivity
 *
 * Lecturer walks to each corner of the classroom and taps "Record Corner".
 */
class BoundarySetupActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvCornerCount: TextView
    private lateinit var tvAccuracy: TextView
    private lateinit var tvCurrentLocation: TextView
    private lateinit var tvCornersList: TextView
    private lateinit var btnRecordCorner: Button
    private lateinit var btnSaveBoundary: Button
    private lateinit var btnClearCorners: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var accuracyIndicator: View

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var currentLocation: Location? = null
    private var currentAccuracyMeters: Float = 999f

    private val recordedCorners = mutableListOf<GeoPoint>()
    private val MIN_CORNERS = 3
    private val MAX_CORNERS = 8
    private val GOOD_ACCURACY_THRESHOLD = 8f
    private val ACCEPTABLE_ACCURACY_THRESHOLD = 15f

    private var subjectId: String = ""
    private var classroomName: String = ""

    companion object {
        const val EXTRA_SUBJECT_ID = "extra_subject_id"
        const val EXTRA_CLASSROOM_NAME = "extra_classroom_name"
        const val LOCATION_PERMISSION_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_boundary_setup)

        subjectId = intent.getStringExtra(EXTRA_SUBJECT_ID) ?: ""
        classroomName = intent.getStringExtra(EXTRA_CLASSROOM_NAME) ?: "Classroom"

        bindViews()
        setupLocationClient()
        setupClickListeners()
        updateUI()

        if (!hasLocationPermission()) {
            requestLocationPermission()
        } else {
            startLocationUpdates()
        }
    }

    private fun bindViews() {
        tvStatus = findViewById(R.id.tv_boundary_status)
        tvCornerCount = findViewById(R.id.tv_corner_count)
        tvAccuracy = findViewById(R.id.tv_gps_accuracy)
        tvCurrentLocation = findViewById(R.id.tv_current_location)
        tvCornersList = findViewById(R.id.tv_corners_list)
        btnRecordCorner = findViewById(R.id.btn_record_corner)
        btnSaveBoundary = findViewById(R.id.btn_save_boundary)
        btnClearCorners = findViewById(R.id.btn_clear_corners)
        progressBar = findViewById(R.id.progress_boundary)
        accuracyIndicator = findViewById(R.id.view_accuracy_indicator)
    }

    private fun setupLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                currentLocation = location
                currentAccuracyMeters = location.accuracy
                updateLocationDisplay(location)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).apply {
            setMinUpdateIntervalMillis(500L)
        }.build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        tvStatus.text = "📡 Acquiring GPS signal..."
    }

    private fun updateLocationDisplay(location: Location) {
        val lat = String.format("%.6f", location.latitude)
        val lng = String.format("%.6f", location.longitude)
        val acc = String.format("%.1f", location.accuracy)

        tvCurrentLocation.text = "📍 $lat, $lng"
        tvAccuracy.text = "Accuracy: ±${acc}m"

        when {
            location.accuracy <= GOOD_ACCURACY_THRESHOLD -> {
                accuracyIndicator.setBackgroundResource(R.drawable.status_dot)
                accuracyIndicator.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_green_light)
                tvStatus.text = "✅ Good GPS fix — ready to record corner"
                btnRecordCorner.isEnabled = recordedCorners.size < MAX_CORNERS
            }
            location.accuracy <= ACCEPTABLE_ACCURACY_THRESHOLD -> {
                accuracyIndicator.setBackgroundResource(R.drawable.status_dot)
                accuracyIndicator.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_orange_light)
                tvStatus.text = "⚠️ Acceptable accuracy — wait for green"
                btnRecordCorner.isEnabled = recordedCorners.size < MAX_CORNERS
            }
            else -> {
                accuracyIndicator.setBackgroundResource(R.drawable.status_dot)
                accuracyIndicator.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_red_light)
                tvStatus.text = "❌ Poor GPS — wait for better fix..."
                btnRecordCorner.isEnabled = false
            }
        }
    }

    private fun setupClickListeners() {
        btnRecordCorner.setOnClickListener {
            val loc = currentLocation ?: return@setOnClickListener
            if (currentAccuracyMeters > ACCEPTABLE_ACCURACY_THRESHOLD) {
                AlertDialog.Builder(this)
                    .setTitle("⚠️ Poor Accuracy")
                    .setMessage("Accuracy is low. Record anyway?")
                    .setPositiveButton("Yes") { _, _ -> recordCorner(loc) }
                    .setNegativeButton("No", null).show()
            } else {
                recordCorner(loc)
            }
        }

        btnClearCorners.setOnClickListener {
            recordedCorners.clear()
            updateUI()
        }

        btnSaveBoundary.setOnClickListener {
            if (recordedCorners.size < MIN_CORNERS) return@setOnClickListener
            saveBoundaryToFirebase()
        }
    }

    private fun recordCorner(location: Location) {
        recordedCorners.add(GeoPoint(location.latitude, location.longitude))
        updateUI()
    }

    private fun saveBoundaryToFirebase() {
        progressBar.visibility = View.VISIBLE
        val center = ClassroomBoundaryManager.calculateCenter(recordedCorners)
        val radius = ClassroomBoundaryManager.calculateRadius(center, recordedCorners)
        val boundary = ClassroomBoundary(
            subjectId = subjectId,
            classroomName = classroomName,
            corners = recordedCorners.toList(),
            centerLat = center.latitude,
            centerLng = center.longitude,
            radiusMeters = radius,
            createdBy = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown",
            createdAt = System.currentTimeMillis()
        )

        ClassroomBoundaryManager.saveBoundary(boundary, {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "✅ Boundary Saved", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }, {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "❌ Error: ${it.message}", Toast.LENGTH_SHORT).show()
        })
    }

    private fun updateUI() {
        tvCornerCount.text = "Corners recorded: ${recordedCorners.size} / $MAX_CORNERS"
        tvCornersList.text = recordedCorners.joinToString("\n") { "${it.latitude}, ${it.longitude}" }
        btnSaveBoundary.isEnabled = recordedCorners.size >= MIN_CORNERS
        btnClearCorners.isEnabled = recordedCorners.isNotEmpty()
    }

    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun requestLocationPermission() = ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_CODE)

    override fun onPause() { super.onPause(); fusedLocationClient.removeLocationUpdates(locationCallback) }
    override fun onResume() { super.onResume(); if (hasLocationPermission()) startLocationUpdates() }
}
