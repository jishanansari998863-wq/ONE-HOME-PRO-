package com.example.domain.document

import com.example.domain.util.QatarFormatters
import java.io.ByteArrayOutputStream
import java.util.Locale

object ReportExporter {

    fun suggestedFileName(reportTitle: String, extension: String): String {
        val clean = reportTitle
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(40)
            .ifBlank { "report" }
        return "onehomepro_${clean}.${extension.lowercase(Locale.US)}"
    }

    fun buildCsvReport(
        reportTitle: String,
        businessName: String,
        lines: List<String>,
        generatedAtEpoch: Long = System.currentTimeMillis()
    ): ByteArray {
        val sb = StringBuilder()
        // UTF-8 BOM so Excel opens Arabic/UTF-8 text cleanly
        sb.append('\uFEFF')
        sb.append(csvRow(listOf("ONE HOME PRO — Qatar Business Edition (QAR)")))
        sb.append(csvRow(listOf("Business", businessName)))
        sb.append(csvRow(listOf("Report", reportTitle)))
        sb.append(csvRow(listOf("Generated (Asia/Qatar)", QatarFormatters.formatDateTime(generatedAtEpoch))))
        sb.append(csvRow(listOf("#", "Detail")))
        if (lines.isEmpty()) {
            sb.append(csvRow(listOf("1", "No records available for this report.")))
        } else {
            lines.forEachIndexed { idx, line ->
                sb.append(csvRow(listOf((idx + 1).toString(), line)))
            }
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun csvRow(cols: List<String>): String =
        cols.joinToString(",") { cell ->
            val neutralized = sanitizeCsvCell(cell)
            val escaped = neutralized.replace("\"", "\"\"")
            "\"$escaped\""
        } + "\r\n"

    internal fun sanitizeCsvCell(raw: String): String {
        val trimmedStart = raw.trimStart()
        return if (trimmedStart.isNotEmpty() && trimmedStart[0] in charArrayOf('=', '+', '-', '@', '\t', '\r')) {
            "'$raw"
        } else {
            raw
        }
    }

    fun buildPdfReport(
        reportTitle: String,
        businessName: String,
        lines: List<String>,
        generatedAtEpoch: Long = System.currentTimeMillis()
    ): ByteArray {
        val wrappedLines = mutableListOf<String>()
        val sourceLines = if (lines.isEmpty()) listOf("No records available for this report.") else lines
        sourceLines.forEachIndexed { idx, raw ->
            val numbered = "${idx + 1}. $raw"
            wrapAsciiLine(numbered, 82).forEach { wrappedLines.add(it) }
        }

        val linesPerPage = 42
        val chunks = wrappedLines.chunked(linesPerPage).ifEmpty { listOf(emptyList()) }
        val pageCount = chunks.size

        val out = ByteArrayOutputStream()
        fun writeAscii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))

        val offsets = mutableListOf<Int>()
        writeAscii("%PDF-1.4\n")

        // Obj 1: Catalog
        offsets.add(out.size())
        writeAscii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        // Obj 2: Pages
        val pageObjIds = (0 until pageCount).map { 4 + it * 2 }
        offsets.add(out.size())
        writeAscii("2 0 obj\n<< /Type /Pages /Kids [${pageObjIds.joinToString(" ") { "$it 0 R" }}] /Count $pageCount >>\nendobj\n")

        // Obj 3: Helvetica Font
        offsets.add(out.size())
        writeAscii("3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n")

        val generatedStr = QatarFormatters.formatDateTime(generatedAtEpoch)
        chunks.forEachIndexed { pageIdx, pageLines ->
            val pageObjId = 4 + pageIdx * 2
            val contentObjId = pageObjId + 1

            val stream = StringBuilder()
            stream.append("BT\n/F1 13 Tf\n48 790 Td\n")
            stream.append("(${pdfEscape("ONE HOME PRO - ${sanitizeAscii(businessName)}")}) Tj\n")
            stream.append("0 -18 Td\n/F1 11 Tf\n")
            stream.append("(${pdfEscape(sanitizeAscii(reportTitle))}) Tj\n")
            stream.append("0 -14 Td\n/F1 9 Tf\n")
            stream.append("(${pdfEscape("Generated: ${sanitizeAscii(generatedStr)} | Page ${pageIdx + 1} of $pageCount")}) Tj\n")
            stream.append("0 -18 Td\n/F1 9 Tf\n14 TL\n")
            pageLines.forEach { row ->
                stream.append("(${pdfEscape(sanitizeAscii(row))}) Tj T*\n")
            }
            stream.append("ET\n")
            val streamBytes = stream.toString().toByteArray(Charsets.US_ASCII)

            offsets.add(out.size())
            writeAscii(
                "$pageObjId 0 obj\n" +
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] " +
                    "/Resources << /Font << /F1 3 0 R >> >> /Contents $contentObjId 0 R >>\nendobj\n"
            )

            offsets.add(out.size())
            writeAscii("$contentObjId 0 obj\n<< /Length ${streamBytes.size} >>\nstream\n")
            out.write(streamBytes)
            writeAscii("\nendstream\nendobj\n")
        }

        val xrefPos = out.size()
        val totalObjs = offsets.size + 1
        writeAscii("xref\n0 $totalObjs\n0000000000 65535 f \n")
        offsets.forEach { off ->
            writeAscii("%010d 00000 n \n".format(Locale.US, off))
        }
        writeAscii("trailer\n<< /Size $totalObjs /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")
        return out.toByteArray()
    }

    private fun wrapAsciiLine(text: String, maxChars: Int): List<String> {
        val safe = sanitizeAscii(text)
        if (safe.length <= maxChars) return listOf(safe)
        val result = mutableListOf<String>()
        var idx = 0
        while (idx < safe.length) {
            val end = (idx + maxChars).coerceAtMost(safe.length)
            result.add(safe.substring(idx, end))
            idx = end
        }
        return result
    }

    private fun sanitizeAscii(input: String): String =
        input.map { ch -> if (ch.code in 32..126) ch else ' ' }.joinToString("")

    private fun pdfEscape(text: String): String =
        text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
}
