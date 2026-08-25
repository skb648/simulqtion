package com.realitycompiler

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class MotorApi(private val baseUrl: String) {
    data class Simulation(val speedRpm: Double, val currentA: Double)
    data class Comparison(val speedDelta: Double, val currentDelta: Double)

    fun analyze(files: List<File>, metadataJson: String): String {
        val boundary = "----RealityCompiler-${UUID.randomUUID()}"
        val conn = (URL("$baseUrl/v1/pipeline/dc-motor").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 5000
            readTimeout = 60000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        conn.outputStream.use { out ->
            out.write(("--$boundary\r\nContent-Disposition: form-data; name=\"metadata_json\"\r\n\r\n$metadataJson\r\n").toByteArray())
            for ((index, file) in files.withIndex()) {
                out.write(("--$boundary\r\nContent-Disposition: form-data; name=\"files\"; filename=\"view_$index.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").toByteArray())
                file.inputStream().use { it.copyTo(out) }
                out.write("\r\n".toByteArray())
            }
            out.write("--$boundary--\r\n".toByteArray())
        }
        if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val reconstruction = json.getJSONObject("reconstruction")
        val twin = json.getJSONObject("digital_twin")
        val unknowns = twin.getJSONArray("unknowns")
        val scale = reconstruction.getString("scale_status")
        return "Digital twin ${twin.getString("id")}: ${twin.getJSONObject("object_type").getString("value")} (INFERRED). Points=${reconstruction.getInt("sparse_point_count")}, reprojection=${reconstruction.getJSONObject("metrics").optDouble("reprojection_error_px", Double.NaN)}, scale=$scale. Unknowns=${unknowns.length()}."
    }

    fun simulate(voltage: Float): Simulation {
        val body = JSONObject().apply { put("voltage_v", voltage); put("duration_s", 2.0); put("dt_s", 0.001) }.toString()
        val json = postJson("/v1/simulations/dc-motor", body)
        val final = json.getJSONObject("final")
        return Simulation(final.getDouble("speed_rpm"), final.getDouble("current_a"))
    }

    fun compare(baselineV: Float, experimentV: Float): Comparison {
        val base = JSONObject().apply { put("voltage_v", baselineV); put("duration_s", 2.0); put("dt_s", 0.001) }
        val exp = JSONObject().apply { put("voltage_v", experimentV); put("duration_s", 2.0); put("dt_s", 0.001) }
        val response = postJson("/v1/experiments/dc-motor", JSONObject().apply { put("baseline", base); put("experiment", exp) })
        val delta = response.getJSONObject("delta")
        return Comparison(delta.getDouble("speed_rpm"), delta.getDouble("current_a"))
    }

    private fun postJson(path: String, json: String): JSONObject {
        val conn = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 5000
            readTimeout = 30000
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { it.write(json.toByteArray()) }
        if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
        return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
    }
}
