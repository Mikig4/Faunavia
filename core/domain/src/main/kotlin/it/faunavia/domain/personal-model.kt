package it.faunavia.domain

import java.time.Instant

/** Private presentation asset, independent of observations and presence evidence. */
data class PersonalModel(
    val taxonId: String,
    val scientificName: String,
    val relativePath: String,
    val sha256: String,
    val byteSize: Long,
    val author: String,
    val source: String,
    val license: String,
    val modifications: String,
    val importedAt: Instant,
) {
    init {
        require(taxonId.isNotBlank() && taxonId.length <= 200)
        require(scientificName.isNotBlank() && scientificName.length <= 300)
        require(relativePath.matches(Regex("models/[a-f0-9-]{36}/model\\.glb")))
        require(sha256.matches(Regex("[a-f0-9]{64}")))
        require(byteSize in 1..20L * 1024 * 1024)
        require(listOf(author, source, license, modifications).all { it.isNotBlank() && it.length <= 2000 })
    }
}

interface PersonalModelRepository {
    suspend fun get(taxonId: String): PersonalModel?
    suspend fun all(): List<PersonalModel>
    suspend fun save(model: PersonalModel)
    suspend fun remove(taxonId: String)
}
