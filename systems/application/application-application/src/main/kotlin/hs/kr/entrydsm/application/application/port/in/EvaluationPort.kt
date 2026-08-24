package hs.kr.entrydsm.application.application.port.`in`

import hs.kr.entrydsm.application.application.port.`in`.command.CalculateEvaluationCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveAcademicRecordCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveCertificatesCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveGedScoresCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveSubjectGradesCommand
import hs.kr.entrydsm.application.application.port.`in`.result.AcademicRecordResult

interface EvaluationPort {
    fun saveSubjectGrades(command: SaveSubjectGradesCommand)
    fun saveGedScores(command: SaveGedScoresCommand)
    fun saveAcademicRecord(command: SaveAcademicRecordCommand): AcademicRecordResult
    fun saveCertificates(command: SaveCertificatesCommand)
    fun calculateResult(command: CalculateEvaluationCommand)
}

