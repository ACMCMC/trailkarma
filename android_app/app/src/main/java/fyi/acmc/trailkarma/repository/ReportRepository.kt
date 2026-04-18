package fyi.acmc.trailkarma.repository

import fyi.acmc.trailkarma.db.TrailReportDao
import fyi.acmc.trailkarma.models.TrailReport
import kotlinx.coroutines.flow.Flow

class ReportRepository(private val dao: TrailReportDao) {
    val allReports: Flow<List<TrailReport>> = dao.getAll()

    suspend fun save(report: TrailReport) = dao.insert(report)
    suspend fun getUnsynced() = dao.getUnsynced()
    suspend fun markSynced(id: String) = dao.markSynced(id)
}
