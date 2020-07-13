package com.cradle.neptune.view

import android.os.Bundle
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cradle.neptune.model.BloodPressure
import com.cradle.neptune.model.GlobalPatient
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.model.ReadingMetadata
import com.cradle.neptune.model.Sex
import com.cradle.neptune.viewmodel.ReadingRecyclerViewAdapter
import com.cradle.neptune.viewmodel.ReadingRecyclerViewAdapter.OnClickElement
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.reading_card_assesment.*
import org.threeten.bp.ZonedDateTime
import java.util.UUID

class GlobalPatientProfileActivity : PatientProfileActivity() {
    private lateinit var globalPatient: GlobalPatient
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //make the network call
        getGlobalPatient()
    }

    private fun getGlobalPatient(){
         globalPatient = intent.getSerializableExtra("globalPatient") as GlobalPatient
        // make the network call to get the patient
        // for now we will mock it.
        val patient = Patient(globalPatient.id,globalPatient.initials,null,33,null,
            Sex.FEMALE,false,"ZONE123",globalPatient.villageNum,
            emptyList(), emptyList())
        populatePatientInfo(patient)
        setupReadingsRecyclerView()

    }
    /**
     * Should always return false here since we dont have any local patient
     */
    override fun getLocalPatient(): Boolean {
        return false
    }

    override fun setupCreatePatientReadingButton() {

    }

    override fun setupReadingsRecyclerView() {
        patientReadings = ArrayList()
        //random reading for now
        for (i in 0 until 10){
            patientReadings.add(Reading(UUID.randomUUID().toString(),globalPatient.id, ZonedDateTime.now(),
                BloodPressure(67+i,78+i,71+i),null, emptyList(),
                null,null, ZonedDateTime.now(),(i%2 ==0), emptyList(),ReadingMetadata()
            ))
        }
        val listAdapter = ReadingRecyclerViewAdapter(patientReadings)
        listAdapter.setOnClickElementListener(object : OnClickElement {
            override fun onClick(readingId: String) {
                Snackbar.make(view,"You must add this patient to your patient lists " +
                    "before editing anything",Snackbar.LENGTH_LONG).show()
            }

            override fun onLongClick(readingId: String): Boolean {
                return true
            }

            override fun onClickRecheckReading(readingId: String) {
                Snackbar.make(view,"You must add this patient to your patient lists " +
                    "before creating a new reading",Snackbar.LENGTH_LONG).show()
            }
        })

        // use linear layout
        val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(this)
        readingRecyclerview.layoutManager = layoutManager
        readingRecyclerview.isNestedScrollingEnabled = false
        readingRecyclerview.adapter = listAdapter
    }

    override fun setupLineChart() {

    }
}
