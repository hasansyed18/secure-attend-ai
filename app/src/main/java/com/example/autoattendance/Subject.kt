package com.example.autoattendance

data class Subject(
    var institutionId: String = "",
    var subjectCode: String = "",
    var subjectName: String = "",
    var department: String = "",
    var semester: String = "",
    var section: String = "",
    var batch: String = "",
    var lecturerId: String = ""
)
