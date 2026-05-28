package com.example.modeltest.data

import java.time.LocalDate

object DateUtils {
    fun todayString(): String = LocalDate.now().toString() // "2026-05-28"

    fun monthEndString(): String {
        val now = LocalDate.now()
        return now.withDayOfMonth(now.lengthOfMonth()).toString()
    }

    fun weekStartString(): String {
        val now = LocalDate.now()
        return now.with(java.time.DayOfWeek.MONDAY).toString()
    }

    fun weekEndString(): String {
        val now = LocalDate.now()
        return now.with(java.time.DayOfWeek.SUNDAY).toString()
    }

    fun weekRange(): Pair<String, String> {
        return weekStartString() to weekEndString()
    }

    fun monthRange(year: Int, month: Int): Pair<String, String> {
        val start = LocalDate.of(year, month, 1)
        val end = start.withDayOfMonth(start.lengthOfMonth())
        return start.toString() to end.toString()
    }

    fun currentMonthRange(): Pair<String, String> {
        val now = LocalDate.now()
        return monthRange(now.year, now.monthValue)
    }

    fun yearRange(year: Int): Pair<String, String> {
        return "$year-01-01" to "$year-12-31"
    }

    fun currentYearRange(): Pair<String, String> {
        return yearRange(LocalDate.now().year)
    }
}
