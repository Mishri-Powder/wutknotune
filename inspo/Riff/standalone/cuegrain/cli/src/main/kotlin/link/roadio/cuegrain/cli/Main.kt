// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain.cli

import link.roadio.cuegrain.*
import java.io.File
import java.util.ServiceLoader
import java.util.concurrent.CancellationException
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    var exit = 0
    val output: Map<String, Any?> =
        try {
            var charset: String? = null
            var strict = false
            var repair = false
            var artwork = false
            var follow = false
            var literal = false
            val paths = mutableListOf<String>()
            var index = 0
            while (index < args.size) {
                val arg = args[index++]
                if (literal) {
                    paths += arg
                    continue
                }
                when (arg) {
                    "--" -> {
                        literal = true
                    }

                    "--help", "-h" -> {
                        println(
                            Json.encode(
                                mapOf(
                                    "schemaVersion" to 1,
                                    "name" to "cuegrain",
                                    "usage" to "cuegrain [--strict] [--charset NAME] [--repair] [--artwork] [--follow-cue] [--] FILE...",
                                    "readOnly" to true,
                                    "output" to "JSON",
                                    "exitCodes" to mapOf("0" to "success", "1" to "input or parse failure", "2" to "invalid arguments"),
                                ),
                            ),
                        )
                        return
                    }

                    "--version" -> {
                        println(Json.encode(mapOf("schemaVersion" to 1, "name" to "cuegrain", "version" to "0.1.0")))
                        return
                    }

                    "--strict" -> {
                        strict = true
                    }

                    "--repair" -> {
                        repair = true
                    }

                    "--artwork" -> {
                        artwork = true
                    }

                    "--follow-cue" -> {
                        follow = true
                    }

                    "--charset" -> {
                        require(index < args.size) { "--charset requires a name" }
                        charset = args[index++]
                        java.nio.charset.Charset
                            .forName(charset)
                    }

                    else -> {
                        require(!arg.startsWith("-")) { "Unknown option: $arg" }
                        paths += arg
                    }
                }
            }
            require(paths.isNotEmpty()) { "At least one input file is required" }
            val detector = ServiceLoader.load(EncodingDetector::class.java).firstOrNull()
            val reader = Cuegrain(ParseOptions(if (strict) ParseMode.STRICT else ParseMode.COMPATIBLE, charset, detector, repair))
            val reports = mutableListOf<Map<String, Any?>>()
            val seen = mutableSetOf<String>()
            val queue = java.util.ArrayDeque(paths)
            while (queue.isNotEmpty()) {
                val file = File(queue.removeFirst())
                if (!seen.add(file.absoluteFile.normalize().path)) continue
                try {
                    FileInput(file).use { input ->
                        val report = reader.read(input, file.extension.lowercase())
                        if (report.status != "ok") exit = 1
                        reports += Json.report(report, artwork)
                        if (follow) {
                            report.sources.filter { it.kind == "cue" && "embeddedIn" !in it.attributes }.flatMap { it.files }.filter { it.resolvedInput == null }.forEach { reference ->
                                val raw = reference.path.replace('\\', File.separatorChar)
                                val ref = File(raw)
                                queue.add(if (ref.isAbsolute) ref.path else File(file.absoluteFile.parentFile, raw).path)
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    exit = 1
                    reports +=
                        Json.report(
                            Report(
                                file.path,
                                emptyList(),
                                listOf(Diagnostic("INPUT_IO", "error", file.path, message = e.message ?: e.javaClass.simpleName)),
                                "error",
                            ),
                        )
                }
            }
            mapOf("schemaVersion" to 1, "ced" to (detector != null), "results" to reports)
        } catch (e: CancellationException) {
            exit = 1
            mapOf("schemaVersion" to 1, "error" to mapOf("code" to "CANCELLED", "message" to "Operation cancelled"))
        } catch (e: Exception) {
            exit = 2
            mapOf("schemaVersion" to 1, "error" to mapOf("code" to "ARGUMENT_INVALID", "message" to (e.message ?: e.javaClass.simpleName)))
        } catch (e: java.util.ServiceConfigurationError) {
            exit = 1
            mapOf(
                "schemaVersion" to 1,
                "error" to mapOf("code" to "CED_UNAVAILABLE", "message" to (e.message ?: "CED initialization failed")),
            )
        }
    println(Json.encode(output))
    exitProcess(exit)
}
