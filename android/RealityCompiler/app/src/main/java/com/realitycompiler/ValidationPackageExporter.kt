package com.realitycompiler

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ValidationPackageExporter {
    fun export(session: ValidationSession, files: Map<String, File>, outputDirectory: File): File {
        outputDirectory.mkdirs()
        val zipFile = File(outputDirectory, "RealityCompilerValidation_${session.sessionId}.zip")
        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            val json = session.toJson().toString(2).toByteArray(Charsets.UTF_8)
            zip.putNextEntry(ZipEntry("validation-session.json"))
            zip.write(json)
            zip.closeEntry()
            files.forEach { (entryName, source) ->
                if (!source.isFile) return@forEach
                zip.putNextEntry(ZipEntry(entryName))
                source.inputStream().buffered().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return zipFile
    }
}
