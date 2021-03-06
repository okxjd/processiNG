package space.okxjd.processiNG

import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.nio.charset.Charset
import java.nio.file.Path as JPath
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.io.path.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.bufferedWriter


@ExperimentalTime
@ExperimentalPathApi
class JoinFiles(var act: String): Common() {

    private var action = cfgAll[act]?.get("action") ?: ""
    private var fullInputPath: JPath = Path(wrkDir, cfgAll[act]?.get("inputdir") ?: "INPUT_DEFAULT")
    private var fullOutputDir: String = Path(wrkDir, cfgAll[act]?.get("outputdir") ?: "OUTPUT_DEFAULT").toString()


    /**
     * Склеивает TXT и CSV файлы (и любые символьные файлы, где есть разделители строк и где значима кодировка).
     *
     * Соответствует action: *join_txt* и *join_csv*
     * */
    inner class JoinTxt {
        private lateinit var fileList: List<JPath>
        private lateinit var ext: String

        fun joinTxt() {
            ext = "txt"
            fileList = getItemsList(fullInputPath, "file", ext)
            join(ext)
        }

        fun joinCsv() {
            ext = "csv"
            fileList = getItemsList(fullInputPath, "file", ext)
            join(ext)
        }

        private fun join(ext: String) {
            createDir(fullOutputDir)
            allRows = 0
            var fileCharset: Charset
            if (fileList.isNotEmpty() && fileList[0].exists()) {
                val doing = measureTime {
                    fileCharset = getCharset(fileList[0].toString())
                    val outputFile = Path(fullOutputDir, "${fileList[0].nameWithoutExtension}_J.$ext")
                        .bufferedWriter(charset = fileCharset, bufferSize = 204800)
                    outputFile.use {
                        fileList.forEach { inputFile ->
                            if (inputFile.exists()) {
                                inputFile.toFile().forEachLine(fileCharset) {
                                    if (allRows % 1000 == 0) print("  >WRK <${allRows}>  ${inputFile.name}\r")
                                    outputFile.write(it)
                                    outputFile.write("\n")
                                    allRows += 1
                                }
                            }
                        }
                        print("\r")
                    }
                }
                logger.info("   cnt: $allRows / time: ${doing.toComponents { days, hours, minutes, seconds, _ -> "${days}d ${hours}h ${minutes}min ${seconds}sec" }}")
                allRows = 0
            }
        }
    }


    /**
     * Склеивает XLSX-файлы в один файл. Если превышен лимит на кол-во строк, создается новый лист.
     *
     * Лимит д.б. целым ([Int]) положительным числом. Если формат или знак не соответствуют условию, то используется
     * универсальное значение по умолчанию: 65000 строк на каждом листе.
     *
     * Может упасть по [OutOfMemoryError].
     *
     * Соответствует action: *join_xlsx*
     * */
    inner class JoinXlsx {
        private var fileList: List<JPath> = getItemsList(fullInputPath, "file", "xlsx")
        private var rowsLimit = try { if ((cfgAll[act]?.get("rows")?.toInt() ?: 65000) > 1000000) 1000000
            else cfgAll[act]?.get("rows")?.toInt() ?: 65000 } catch (e: NumberFormatException) {
            logger.info("${cfgAll[act]?.get("rows").toString()} is not valid positive Int number; 65000 will be used."); 65000 }

        init {
            rowsLimit = if (rowsLimit > 0) rowsLimit else 65000
        }

        fun joinXlsx() {
            createDir(fullOutputDir)
            allRows = 0
            if (fileList.isNotEmpty()) {
                val doing = measureTime {
                    val xlWb = SXSSFWorkbook(100)
                    var xlWs = xlWb.createSheet("PAGE_$sheetCnt")
                    var rw = xlWs.createRow(0)
                    var rowOnSheet = 0
                    var cellCnt = 0
                    fileList.forEach { inputFile ->
                        if (inputFile.exists()) {
                            logger.info("> ${inputFile.name}")
                            val pkg: OPCPackage = OPCPackage.open(inputFile.toFile())
                            pkg.use { p ->
                                XSSFWorkbook(p).use { ifl ->
                                    ifl.sheetIterator().forEach { sh ->
                                        sh.rowIterator().forEach { r ->
                                            r.cellIterator().forEach { c ->
                                                rw.createCell(cellCnt, CellType.STRING)
                                                    .setCellValue(c.toString())
                                                cellCnt += 1
                                            }
                                            cellCnt = 0
                                            rowOnSheet += 1
                                            if (rowOnSheet % rowsLimit == 0) {
                                                sheetCnt += 1
                                                rowOnSheet = 0
                                                xlWs = xlWb.createSheet("PAGE_$sheetCnt")
                                            }
                                            rw = xlWs.createRow(rowOnSheet)
                                            allRows += 1
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Path(fullOutputDir, "${fileList[0].nameWithoutExtension}_J.xlsx").toFile()
                        .outputStream()
                        .use {
                            xlWb.write(it)
                            xlWb.dispose()
                            xlWb.close()
                        }
                 }
                logger.info(" > cnt: $allRows / time: ${doing.toComponents { days, hours, minutes, seconds, _ -> "${days}d ${hours}h ${minutes}min ${seconds}sec" }}")
                allRows = 0
            }
        }
    }

}
