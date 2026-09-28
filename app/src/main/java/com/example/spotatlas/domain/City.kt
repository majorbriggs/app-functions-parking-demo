package com.example.spotatlas.domain

/** A city SpotAtlas has parking coverage for. */
data class City(
    val id: String,
    val displayName: String,
    val country: String,
    /** Map camera target when the city is selected. */
    val center: GeoPoint,
    /** Alternative spellings an agent or user may type, e.g. "warszawa" for Warsaw. */
    val aliases: Set<String> = emptySet(),
)
