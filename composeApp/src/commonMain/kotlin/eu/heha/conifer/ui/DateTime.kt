package eu.heha.conifer.ui

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

fun LocalDate.print() = LocalDate.Formats.ISO.format(this)
fun LocalTime.print() = LocalTime
    .Format {
        hour()
        chars(":")
        minute()
    }
    .format(this)