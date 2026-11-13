package com.example.domain.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object QatarFormatters {
    val qatarTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Qatar")

    fun formatQar(fils: Long): String {
        val negative = fils < 0
        val absFils = abs(fils)
        val whole = absFils / 100
        val cents = absFils % 100
        val nf = NumberFormat.getIntegerInstance(Locale.US)
        val formattedWhole = nf.format(whole)
        val sign = if (negative) "-" else ""
        return "${sign}QAR $formattedWhole.${"%02d".format(cents)}"
    }

    fun parseQarToFils(input: String): Long? {
        val cleaned = input.trim().replace(",", "").replace("QAR", "", ignoreCase = true).trim()
        if (cleaned.isEmpty()) return null
        val bd = cleaned.toBigDecimalOrNull() ?: return null
        return bd.movePointRight(2).toLong()
    }

    fun formatDate(epochMillis: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
        sdf.timeZone = qatarTimeZone
        return sdf.format(Date(epochMillis))
    }

    fun formatDateTime(epochMillis: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US)
        sdf.timeZone = qatarTimeZone
        return "${sdf.format(Date(epochMillis))} AST"
    }

    fun periodKey(epochMillis: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        sdf.timeZone = qatarTimeZone
        return sdf.format(Date(epochMillis))
    }

    fun periodLabel(epochMillis: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.US)
        sdf.timeZone = qatarTimeZone
        return sdf.format(Date(epochMillis))
    }

    fun dateKey(epochMillis: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = qatarTimeZone
        return sdf.format(Date(epochMillis))
    }
}
