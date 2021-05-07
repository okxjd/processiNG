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
import java.io.FileOutputStream
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
class Splitter: Common() {
    init {
        createDir(fullOutputDir)
    }


    /**
     * Делитель TXT-файлов (на самом деле любых символьных файлов с разделителями строк - [System].[lineSeparator()]).
     * Количество строк из конфига.
     *
     * Соответствует action: *split_txt*
     * */
    inner class SplitTXT {
        private var fileList: List<JPath> = getItemsList(fullInputPath, "file", "txt")
        fun split() {
            fileList.forEach { inputFile ->
                allRows = 0
                if (inputFile.exists()) {
                    logger.info("> ${inputFile.name}")
                    val doing = measureTime {
                        val fileCharset = getCharset(inputFile.toString())
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
                    logger.info("   cnt: $allRows / time: ${doing.toComponents { days, hours, minutes, seconds, _ -> "${days}d ${hours}h ${minutes}min ${seconds}sec" }}")
                } else {
                    logger.info("File [${inputFile.name}] not found !!!")
                }
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
        private var fileList: List<JPath> = getItemsList(fullInputPath, "file", "csv")

        @KotlinCsvExperimental
        fun split() {
            fileList.forEach { inputFile ->
                allRows = 0
                if (inputFile.exists()) {
                    logger.info("> ${inputFile.name}")
                    val doing = measureTime {
                        val prefix: String = inputFile.nameWithoutExtension
                        delimiterCSV = getCsvDelimiter(inputFile.toString())
                        val fileCharset = getCharset(inputFile.toString())
                        val csvWriteCtx = csvWriter {
                            charset = fileCharset.toString()
                            delimiter = delimiterCSV
                            nullCode = ""
                            lineTerminator = "\n"
                            outputLastLineTerminator = true
                            quote {
                                mode = WriteQuoteMode.ALL
                                char = quoteCharCSV
                            }
                        }
                        var outputFile = FileOutputStream(Path(fullOutputDir, "$prefix-$outFileCount.csv").toString())
                        var csvW = csvWriteCtx.openAndGetRawWriter(outputFile)
                        val csvR = csvReader {
                            charset = fileCharset.toString()
                            quoteChar = quoteCharCSV
                            delimiter = delimiterCSV
                            escapeChar = escapeCharCSV
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
                                    outputFile = FileOutputStream(Path(fullOutputDir, "$prefix-$outFileCount.csv").toString())
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
                    logger.info("   cnt: $allRows / time: ${doing.toComponents { days, hours, minutes, seconds, _ -> "${days}d ${hours}h ${minutes}min ${seconds}sec" }}")
                } else {
                    logger.info("File [${inputFile.name}] not found !!!")
                }
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
        private var fileList: List<JPath> = getItemsList(fullInputPath, "file", "xlsx")

        fun split() {
            when (action) {
                "split_xlsx_rows" -> {
                    fileList.forEach { splitXlsxRows(it) }
                }
                "split_xlsx_mask" -> {
                    fileList.forEach { splitXlsxMask(it) }
                }
            }
        }

        private fun splitXlsxMask(inputFile: JPath) {
            logger.info("SPLIT XLSX by MASK not implemented yet.")
            logger.info("> ${inputFile.name}")
            allRows = 0
            TODO()
        }

        private fun splitXlsxRows(inputFile: JPath) {
            allRows = 0
            outFileCount = 0
            if (inputFile.exists()) {
                logger.info("> ${inputFile.name}")
                val doing = measureTime {
                    val prefix: String = inputFile.nameWithoutExtension
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
                    }
                    print("\r")
                }
                logger.info("   cnt: $allRows / time: ${doing.toComponents { days, hours, minutes, seconds, _ -> "${days}d ${hours}h ${minutes}min ${seconds}sec" }}")
            } else {
                logger.info("File [${inputFile.name}] not found !!!")
            }
        }

        inner class SheetHandler(
                private val sst: SharedStringsTable,
                private val prefix: String
                ): DefaultHandler() {
            private var lastContents: String? = null
            private var nextIsString = false
            private var inlineStr = false
            private var book: SXSSFWorkbook = SXSSFWorkbook(100)
            private var page: SXSSFSheet = book.createSheet("PAGE_0")
            private var fileNum: Int = 0
            private var outFileName: String = Path(fullOutputDir, "$prefix-$fileNum.xlsx").toString()
            private var outStream: FileOutputStream = FileOutputStream(outFileName)
            private var rowsOnSheet: Int = 0
            private var rawList: MutableList<String> = mutableListOf()
            private var numSheets: Int = -2

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
                        try {
                            book.write(outStream)
                        } finally {
                            closeBook()
                        }
                        rowsOnSheet = -1
                        fileNum += 1
                        outFileName = Path(fullOutputDir, "$prefix-$fileNum.xlsx").toString()
                        book = SXSSFWorkbook(100)
                        page = book.createSheet("PAGE_0")
                        outStream = FileOutputStream(outFileName)
                    }
                    rowsOnSheet += 1
                    allRows += 1
                    rawList = mutableListOf()
                }
                if (name == "worksheet") {
                    if (book.numberOfSheets == numSheets) {
                        try {
                            book.write(outStream)
                        } finally {
                            closeBook()
                        }
                    }
                }
            }

            private fun closeBook() {
                book.dispose()
                book.close()
                outStream.close()
            }

            override fun characters(ch: CharArray, start: Int, length: Int) { lastContents += String(ch, start, length) }

        }
    }
}


