package it.faunavia.local

import it.faunavia.domain.PersonalModel
import java.time.Instant

internal fun PersonalModelRow.toDomain() = PersonalModel(taxonId, scientificName, relativePath, sha256, byteSize,
    author, source, license, modifications, Instant.parse(importedAt))
internal fun PersonalModel.toRow() = PersonalModelRow(taxonId, scientificName, relativePath, sha256, byteSize,
    author, source, license, modifications, importedAt.toString())
