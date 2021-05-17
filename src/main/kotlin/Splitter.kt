package space.okxjd.processiNG

import com.github.doyaaaaaken.kotlincsv.client.KotlinCsvExperimental
import com.github.doyaaaaaken.kotlincsv.dsl.context.WriteQuoteMode
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.util.XMLHelper
import org.apache.poi.xssf.eventusermodel.XSSFReader
import org.apache.poi.xssf.model.SharedStringsTable
import org.apache.poi.xssf.streaming.SXSSFRow
import org.apache.poi.xssf.streaming.SXSSFSheet
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.xml.sax.Attributes
import org.xml.sax.ContentHandler
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import kotlin.io.outputStream
import java.nio.file.Path as JPath
import kotlin.io.path.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.bufferedWriter
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime


/**
 * Дополнение для класса [SXSSFRow] - запись стандартного [List]<[String]>.
* */
fun SXSSFRow.writeListAsRow(cells: List<String>)  {
    cells.forEachIndexed { i, v ->
        createCell(i, CellType.STRING)
            .setCellValue(v)
    }
}


@ExperimentalTime
@ExperimentalPathApi
class Splitter(act: String) {
    private var action = Common.cfgAll[act]?.get("action") ?: ""
    private var fullInputPath: JPath = Path(Common.wrkDir, Common.cfgAll[act]?.get("inputdir") ?: "INPUT_DEFAULT")
    private var fullOutputDir: String = Path(Common.wrkDir, Common.cfgAll[act]?.get("outputdir") ?: "OUTPUT_DEFAULT").toString()
    private var rowsLimit = try { if ((Common.cfgAll[act]?.get("rows")?.toInt() ?: 65000) > 1000000) 1000000
        else Common.cfgAll[act]?.get("rows")?.toInt() ?: 65000 } catch (e: NumberFormatException) {
        Common.logger.info("${Common.cfgAll[act]?.get("rows").toString()} is not valid positive Int number; 65000 will be used."); 65000 }
    private var outFileCount: Int = 0
    var allRows = 0

    init {
        Common.createDir(fullOutputDir)
        rowsLimit = if (rowsLimit > 0) rowsLimit else 65000
    }


    /**
     * Делитель TXT-файлов (на самом деле любых символьных файлов с разделителями строк - [System].[lineSeparator()]).
     * Количество строк из конфига.
     *
     * Соответствует action: *split_txt*
     * */
    inner class SplitTXT {
        private var fileList: List<JPath> = Common.getItemsList(fullInputPath, "file", "txt")
        fun split() {
            fileList.forEach { inputFile ->
                allRows = 0
                if (inputFile.exists()) {
                    Common.logger.info("  > ${inputFile.name}")
                    val doing = measureTime {
                        val fileCharset = Common.getCharset(inputFile.toString())
                        val prefix: String = inputFile.nameWithoutExtension
                        var outputFile = Path(fullOutputDir, "$prefix-$outFileCount.txt")
                            .bufferedWriter(charset = fileCharset, bufferSize = 102400)
                        inputFile.toFile().forEachLine(fileCharset) {
                            if (allRows % 1000 == 0) print("  >WRK <$allRows>\r")
                            outputFile.write(it + System.lineSeparator())
                            if (allRows % rowsLimit == 0) {
                                outputFile.close()
                                outFileCount += 1
                                outputFile = Path(fullOutputDir, "$prefix-$outFileCount.txt")
                                    .bufferedWriter(charset = fileCharset, bufferSize = 102400)
                            }
                            allRows += 1
                        }
                        outputFile.close()
                        print("\r")
                    }
                    Common.logger.info("   cnt: $allRows / time: ${doing.toComponents { days, hours, minutes, seconds, _ -> "${days}d ${hours}h ${minutes}min ${seconds}sec" }}")
                } else {
                    Common.logger.info("File [${inputFile.name}] not found !!!")
                }
                allRows = 0
            }
        }
    }


    /**
     * Делитель CSV-файлов. Кодировку и разделитель полей определяет по исходному файлу. Количество строк из конфига.
     *
     * Разделитель строк - '\n'
     *
     * Соответствует action: *split_csv*
     * */
    inner class SplitCSV {
        private var fileList: List<JPath> = Common.getItemsList(fullInputPath, "file", "csv")

        @KotlinCsvExperimental
        fun split() {
            fileList.forEach { inputFile ->
                allRows = 0
                if (inputFile.exists()) {
                    Common.logger.info("  > ${inputFile.name}")
                    val doing = measureTime {
                        val prefix: String = inputFile.nameWithoutExtension
                        Common.delimiterCSV = Common.getCsvDelimiter(inputFile.toString())
                        val fileCharset = Common.getCharset(inputFile.toString())
                        val csvWriteCtx = csvWriter {
                            charset = fileCharset.toString()
                            delimiter = Common.delimiterCSV
                            nullCode = ""
                            lineTerminator = "\n"
                            outputLastLineTerminator = true
                            quote {
                                mode = WriteQuoteMode.ALL
                                char = Common.quoteCharCSV
                            }
                        }
                        var outputFile = Path(fullOutputDir, "$prefix-$outFileCount.csv").toFile().outputStream()
                        var csvW = csvWriteCtx.openAndGetRawWriter(outputFile)
                        val csvR = csvReader {
                            charset = fileCharset.toString()
                            quoteChar = Common.quoteCharCSV
                            delimiter = Common.delimiterCSV
                            escapeChar = Common.escapeCharCSV
                            skipEmptyLine = true
                            skipMissMatchedRow = true
                        }
                        csvR.open(inputFile.toString()) {
                            var row = readNext()
                            while (row != null) {
                                if (allRows % 1000 == 0) print("  >WRK <$allRows>\r")
                                if (allRows > 0 && (allRows % rowsLimit == 0)) {
                                    csvW.close()
                                    outputFile.close()
                                    outFileCount += 1
                                    outputFile = Path(fullOutputDir, "$prefix-$outFileCount.csv").toFile().outputStream()
                                    csvW = csvWriteCtx.openAndGetRawWriter(outputFile)
                                }
                                csvW.writeRow(row)
                                allRows += 1
                                row = readNext()
                            }
                        }
                        csvW.close()
                        outputFile.close()
                        print("\r")
                    }
                    Common.logger.info("   cnt: $allRows / time: ${doing.toComponents { days, hours, minutes, seconds, _ -> "${days}d ${hours}h ${minutes}min ${seconds}sec" }}")

                } else {
                    Common.logger.info("File [${inputFile.name}] not found !!!")
                }
                allRows = 0
            }
        }
    }


    /** Делитель XLSX-файлов
     *
     * 2 разновидности в зависимости от action:
     * - split_xlsx_mask - делитель по маске - еще не реализован
     * - split_xlsx_rows - делитель по количеству строк
     *
     * Может делить очень большие файлы (тестировался на файле ~2,5М строк) с относительно небольшим расходом памяти.
     *
     * Соответствует action: *split_xlsx_rows* и *split_xlsx_mask*
     * */
    inner class SplitXLSX {
        private var fileList: List<JPath> = Common.getItemsList(fullInputPath, "file", "xlsx")
        private var book: SXSSFWorkbook = SXSSFWorkbook(100)
        private var page: SXSSFSheet = book.createSheet("PAGE_0")
        private var numSheets: Int = 0
        private var rowsOnSheet: Int = 0
        private var rawList: MutableList<String> = mutableListOf()

        fun split() {
            when (action) {
                "split_xlsx_rows" -> {
                    fileList.forEach {
                        splitXlsxRows(it)
                    }
                }
                "split_xlsx_mask" -> {
                    fileList.forEach {
                        splitXlsxMask(it)
                    }
                }
            }
        }

        private fun splitXlsxMask(inputFile: JPath) {
            Common.logger.info("SPLIT XLSX by MASK not implemented yet.")
            Common.logger.info("> ${inputFile.name}")
            allRows = 0
            TODO()
        }

        private fun splitXlsxRows(inputFile: JPath) {
            allRows = 0
            if (inputFile.exists()) {
                Common.logger.info("  > ${inputFile.name}")
                val doing = measureTime {
                    val prefix = inputFile.nameWithoutExtension
                    val pkg = OPCPackage.open(inputFile.toString())
                    val r = XSSFReader(pkg)
                    val sst = r.sharedStringsTable
                    val parser = XMLHelper.newXMLReader()
                    val handler: ContentHandler = SheetHandler(sst, prefix)
                    parser.contentHandler = handler
                    val sheets = r.sheetsData
                    pkg.use {
                        sheets.forEach { s ->
                            parser.parse(InputSource(s))
                        }
                        book.use { b ->
                            Path(fullOutputDir, "$prefix-$outFileCount.xlsx").toFile().outputStream().use { s ->
                                b.write(s)
                            }
                        }
                    }
                    print("\r")
                }
                Common.logger.info("   cnt: $allRows / time: ${doing.toComponents { days, hours, minutes, seconds, _ -> "${days}d ${hours}h ${minutes}min ${seconds}sec" }}")
            } else {
                Common.logger.info("File [${inputFile.name}] not found !!!")
            }
        }

        inner class SheetHandler(
                private val sst: SharedStringsTable,
                private val filePrefix: String
                ): DefaultHandler() {
            private var lastContents: String? = null
            private var nextIsString = false
            private var inlineStr = false

            override fun startElement(uri: String, localName: String, name: String, attributes: Attributes) {
                if (name == "c") {
                    val cellType = attributes.getValue("t")
                    nextIsString = cellType != null && cellType == "s"
                    inlineStr = false
                    if (cellType != null && cellType.equals("inlineStr")) {
                        inlineStr = true
                    }
                }
                if (name == "row") {
                    if (allRows % 1000 == 0) print("  >WRK <${allRows}>\r")
                }
                if (name == "worksheet") {
                    numSheets += 1
                }
                lastContents = ""
            }

            override fun endElement(uri: String, localName: String, name: String) {
                if (nextIsString) {
                    val idx = lastContents!!.toInt()
                    lastContents = sst.getItemAt(idx).string
                    nextIsString = false
                }
                if(name == "v" || (inlineStr && name == "c")) {
                    rawList.add(lastContents ?: "")
                }
                if (name == "row") {
                    page.createRow(rowsOnSheet).writeListAsRow(rawList)
                    if (rowsOnSheet > 0 && allRows % rowsLimit == 0) {
                        book.use { b ->
                            Path(fullOutputDir, "$filePrefix-$outFileCount.xlsx").toFile().outputStream().use { s ->
                                b.write(s)
                            }
                        }
                        outFileCount += 1
                        book = SXSSFWorkbook(100)
                        page = book.createSheet("PAGE_0")
                        rowsOnSheet = -1
                    }
                    rowsOnSheet += 1
                    allRows += 1
                    rawList = mutableListOf()
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) { lastContents += String(ch, start, length) }

        }
    }
}


