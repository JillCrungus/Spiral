package org.abimon.spiral.core.formats.text

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.abimon.spiral.core.data.SpiralData
import org.abimon.spiral.core.formats.SpiralFormat
import org.abimon.spiral.core.formats.scripting.NonstopFormat
import org.abimon.spiral.core.readMapValue
import org.abimon.spiral.core.writeShort
import org.abimon.visi.io.DataSource
import java.io.OutputStream

abstract class JacksonFormat: SpiralFormat {
    override val conversions: Array<SpiralFormat> = emptyArray() //We should not be doing any automated conversions
    val manualConversions: Array<SpiralFormat> = arrayOf(NonstopFormat) //But we should allow manual conversions

    override fun canConvert(format: SpiralFormat): Boolean = format in manualConversions
    abstract val MAPPER: ObjectMapper

    override fun isFormat(source: DataSource): Boolean {
        try {
            source.use { stream -> MAPPER.readValue(stream, Map::class.java) }
            return true
        } catch (json: JsonParseException) {
        } catch (json: JsonMappingException) {
        }

        try {
            source.use { stream -> MAPPER.readValue(stream, List::class.java) }
            return true
        } catch (json: JsonParseException) {
        } catch (json: JsonMappingException) {
        }

        return false
    }

    override fun convert(format: SpiralFormat, source: DataSource, output: OutputStream, params: Map<String, Any?>): Boolean {
        if(super.convert(format, source, output, params)) return true

        when(format) {
            NonstopFormat -> {
                val debateMap = source.use { stream -> MAPPER.readMapValue(stream, String::class, Any::class) }

                val duration = debateMap["duration"]?.toString()?.toIntOrNull() ?: throw IllegalArgumentException("${source.location} is an invalid Nonstop Debate $name file ('duration' is either not present or not a number)")
                val sections = (debateMap["sections"] as? List<*> ?: throw IllegalArgumentException("${source.location} is an invalid Nonstop Debate $name file ('sections' is either not present or not a list)"))
                        .filterIsInstance(Map::class.java)
                        .map { theMap -> theMap.filter { (a, b) -> a is String && b is Int }.mapKeys { (a) ->
                            val op = a as String
                            when {
                                op.startsWith("0x") -> return@mapKeys op.substring(2).toInt(16)
                                op.matches("\\d+".toRegex()) -> return@mapKeys op.toInt()
                                SpiralData.nonstopOpCodes.entries.any { (_, name) -> name.equals(op, true) } -> return@mapKeys SpiralData.nonstopOpCodes.entries.first { (_, name) -> name.equals(op, true) }.key
                                else -> return@mapKeys 0x00
                            }
                        }.mapValues { (_, b) -> b as Int } }

                if(sections.isEmpty())
                    throw throw IllegalArgumentException("${source.location} is an invalid Nonstop Debate $name file ('sections' is empty)")

                output.writeShort(duration / 2, unsigned = true)
                output.writeShort(sections.size, unsigned = true)

                val max = sections.first().entries.sortedBy { (index) -> index }.last().key + 1 //Zero Indexing

                for(section in sections) {
                    for(i in 0 until max)
                        output.writeShort(section[i] ?: 0x00, unsigned = true)
                }
            }
        }

        return false
    }
}