package com.hakan.notdefteri.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val TR: Locale = Locale.forLanguageTag("tr")

fun formatDateTime(millis: Long): String = SimpleDateFormat("d MMM yyyy HH:mm", TR).format(Date(millis))
fun formatDate(millis: Long): String = SimpleDateFormat("d MMMM yyyy, EEEE", TR).format(Date(millis))
fun formatTime(millis: Long): String = SimpleDateFormat("HH:mm", TR).format(Date(millis))

fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

val ReminderOptions = listOf(
    0 to "Önceden alarm yok",
    5 to "5 dakika önce",
    10 to "10 dakika önce",
    15 to "15 dakika önce",
    30 to "30 dakika önce",
    60 to "1 saat önce",
    120 to "2 saat önce",
    1440 to "1 gün önce",
)
