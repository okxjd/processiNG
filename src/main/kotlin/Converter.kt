package space.okxjd.processiNG

import org.apache.commons.csv.CSVFormat
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.apache.poi.hssf.usermodel.HSSFRow
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Path as JPath
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.io.path.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension


@ExperimentalTime
@ExperimentalPathApi
class Converter: Common() {
    private lateinit var fileList: List<JPath>

    fun convert() {
        createDir(fullOutputDir)

        val coolExt = mapOf(
            "csv2xlsx" to "csv",
            "csv2xls"  to "csv",
            "xls2xlsx" to "xls",
            "xlsx2xls" to "xlsx"
        )

        val coolMap = mapOf(
            "csv2xlsx" to ::csv2excel,
            "csv2xls"  to ::csv2excel,
            "xls2xlsx" to ::xls2xlsx,
            "xlsx2xls" to ::xlsx2xls
        )

        fileList = getItemsList(fullInputPath, "file", coolExt[action])
        fileList.forEach { coolMap[action]?.invoke(it) }
    }

    /**
     * Конвертер CSV -> XLSX, XLS (в зависимости от action)
     * Запись информации из CSV-файла произвольного размера в файл XLS или XLSX в соответствии
     * с ограничениями этих форматов на количество строк на листе и количество символов в ячейке.
     * Кодировка и разделитель полей определяются "полуавтоматически" с помощью Common.getCharset() и Common.getCsvDelimiter()
     *
     * Конвертер в XLS кушает много памяти - может упасть по [OutOfMemoryError].
     *
     * Соответствует action: *csv2xlsx* и *csv2xls*
     */
    private fun csv2excel(inputFile: JPath) {
        if (inputFile.exists()) {
            logger.info(inputFile.name)
            val doing = measureTime {
                val prefix: String = inputFile.nameWithoutExtension
                val ext: String = if (isToOldExcelVersion(action)) "xls" else "xlsx"
                val xlWb = if (isToOldExcelVersion(action)) HSSFWorkbook() else SXSSFWorkbook(100)
                val outputFile = Path(fullOutputDir, "$prefix.$ext")
                val outputStream = FileOutputStream(outputFile.toString())
                delimiterCSV = getCsvDelimiter(inputFile.toString())
                val csvCharset = getCharset(inputFile.toString())
                val csvReader = File(inputFile.toString()).bufferedReader(csvCharset)
                val parser = CSVFormat.EXCEL
                    .withDelimiter(delimiterCSV)
                    .withQuote(quoteCharCSV)
                    .withTrim(true)
                    .withIgnoreEmptyLines(true)
                    .withAllowMissingColumnNames(true)
                    .withAutoFlush(true)
                    .withIgnoreSurroundingSpaces(true)
                    .parse(csvReader)
                try {
                    var xlWs = xlWb.createSheet("PAGE_$sheetCnt")
                    var rw = xlWs.createRow(0)
                    var rowOnSheet = 0
                    parser.forEachIndexed { index, s ->
                        if (index % 1000 == 0) print("  >WRK <$index>\r")
                        allRows = index
                        if (allRows > 0 && allRows % rowsLimit == 0) {
                            sheetCnt += 1
                            xlWs = xlWb.createSheet("PAGE_$sheetCnt")
                            rw = xlWs.createRow(0)
                            rowOnSheet = 0
                        }
                        rw = xlWs.createRow(rowOnSheet)
                        s.forEachIndexed { indext, st ->
                            rw.createCell(indext, CellType.STRING).setCellValue(
                                st?.replace(
                                    Regex(
                                        "[ \n\r\u2028\u2029\t\u1680\u180e\u2000-\u200a\u202f\u205f\u3000]+",
                                        RegexOption.MULTILINE
                                    ), " "
                                ) ?: ""
                                    .take(32765)
                                    .replace(Regex("[ ]{2,}"), " ")
                                    .trim()
                            )
                        }
                        rowOnSheet += 1
                    }
                } catch (e: OutOfMemoryError) {
                    logger.info("Error: OutOfMemoryError")
                } finally {
                    xlWb.write(outputStream)
                    xlWb.close()
                    outputStream.close()
                    parser.close()
                    csvReader.close()
                    print("\r")
                }
            }
            logger.info("   cnt: $allRows / time: ${doing.toComponents { days, hours, minutes, seconds, _
                -> "${days}d ${hours}h ${minutes}min ${seconds}sec" }}")
        } else {
            logger.info("File [${inputFile.name}] not found !!!")
        }
    }



    /**
     * Конвертер XLS -> XLSX.
     * Все ячейки рассматриваются как текстовые, форматирование не сохраняется.
     *
     * Соответствует action: *xls2xlsx*
     */
    private fun xls2xlsx(inputFile: JPath) {
        var rowCnt = 0
        var cellCnt = 0
        val outputFile = Path(fullOutputDir, inputFile.nameWithoutExtension + ".xlsx")

        val oldB = HSSFWorkbook(FileInputStream(inputFile.toFile()))
        val newB = SXSSFWorkbook(100)
        var newS = newB.createSheet("PAGE_$sheetCnt")
        if (inputFile.exists()) {
            logger.info(inputFile.name)
            print("\r")
            val doing = measureTime {
                try {
                    oldB.sheetIterator().forEach { sheet ->
                        sheet.rowIterator().forEach { row ->
                            val newR = newS.createRow(rowCnt)
                            if (allRows % 1000 == 0) print("  >WRK <${allRows}>\r")
                            allRows += 1
                            row.cellIterator().forEach { cell ->
                                newR
                                    .createCell(cellCnt, CellType.STRING)
                                    .setCellValue(cell.toString())
                                cellCnt += 1
                            }
                            cellCnt = 0
                            rowCnt += 1
                            if (rowCnt % rowsLimit == 0) {
                                sheetCnt += 1
                                rowCnt = 0
                                newS = newB.createSheet("PAGE_$sheetCnt")
                            }
                        }
                    }
                } finally {
                    oldB.close()
                    val outF = FileOutputStream(outputFile.toString())
                    newB.write(outF)
                    newB.dispose()
                    newB.close()
                    outF.close()
                    print("\r")
                }
            }
            logger.info("   cnt: $allRows / time: ${doing.toComponents { days, hours, minutes, seconds, _ -> "${days}d ${hours}h ${minutes}min ${seconds}sec" }}")
        }
        else {
            logger.info("File [${inputFile.name}] not found !!!")
        }
    }


    /**
     * Конвертер XLSX -> XLS
     * Все ячейки рассматриваются как текстовые, форматирование не сохраняется.
     *
     * Соответствует action: *xlsx2xls*
     */
    private fun xlsx2xls(inputFile: JPath) {
        var rowCnt = 0
        var cellCnt = 0
        val outputFile = Path(fullOutputDir, inputFile.nameWithoutExtension + ".xls")

        val pkg = OPCPackage.open(inputFile.toFile())
        val oldB = XSSFWorkbook(pkg)
        val newB = HSSFWorkbook()
        var newS = newB.createSheet("PAGE_$sheetCnt")

        if (inputFile.exists()) {
            logger.info(inputFile.name)
            print("\r")
            val doing = measureTime {
                try {
                    oldB.sheetIterator().forEach { sheet ->
                        var newR: HSSFRow
                        sheet.rowIterator().forEach { row ->
                            if (allRows % 1000 == 0) print("  >WRK <${allRows}>\r")
                            allRows += 1
                            newR = newS.createRow(rowCnt)
                            row.cellIterator().forEach { cell ->
                                newR
                                    .createCell(cellCnt, CellType.STRING)
                                    .setCellValue(cell.toString())
                                cellCnt += 1
                            }
                            cellCnt = 0
                            rowCnt += 1
                            if (rowCnt % rowsLimit == 0) {
                                sheetCnt += 1
                                rowCnt = 0
                                newS = newB.createSheet("PAGE_$sheetCnt")
                            }
                        }
                    }
                } finally {
                    oldB.close()
                    pkg.revert()
                    val outF = FileOutputStream(outputFile.toString())
                    newB.write(outF)
                    newB.close()
                    outF.close()
                    print("\r")
                }
            }
            logger.info("   cnt: $allRows / time: ${doing.toComponents { days, hours, minutes, seconds, _ -> "${days}d ${hours}h ${minutes}min ${seconds}sec" }}")
        }
        else {
            logger.info("File [${inputFile.name}] not found !!!")
        }
    }
}
