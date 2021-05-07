package space.okxjd.processiNG

import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.FileOutputStream
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
class JoinFiles: Common() {


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

        fun join() {
            createDir(fullOutputDir)
            allRows = 0
            if (fileList.isNotEmpty()) {
                val doing = measureTime {
                    val outputFile = Path(fullOutputDir, "${fileList[0].nameWithoutExtension}_J.xlsx")
                    val outputStream = FileOutputStream(outputFile.toString())
                    val xlWb = SXSSFWorkbook(100)
                    var xlWs = xlWb.createSheet("PAGE_$sheetCnt")
                    var rw = xlWs.createRow(0)
                    var rowOnSheet = 0
                    var cellCnt = 0
                    fileList.forEach { inputFile ->
                        if (inputFile.exists()) {
                            logger.info("> ${inputFile.name}")
                            val pkg: OPCPackage = OPCPackage.open(inputFile.toFile())
                            val iFile = XSSFWorkbook(pkg)
                            try {
                                iFile.sheetIterator().forEach { sh ->
                                    sh.rowIterator().forEach { r ->
                                        r.cellIterator().forEach { c ->
                                            rw.createCell(cellCnt, CellType.STRING)
                                                .setCellValue(c.toString())
                                            cellCnt += 1
                                        }
                                        rowOnSheet += 1
                                        if (rowOnSheet % rowsLimit == 0) {
                                            sheetCnt += 1
                                            rowOnSheet = 0
                                            xlWs = xlWb.createSheet("PAGE_$sheetCnt")
                                        }
                                        rw = xlWs.createRow(rowOnSheet)
                                    }
                                }
                            } finally {
                                iFile.close()
                                pkg.close()
                            }
                        }
                    }
                    xlWb.write(outputStream)
                    xlWb.dispose()
                    xlWb.close()
                    outputStream.close()
                }
                logger.info(" > cnt: $allRows / time: ${doing.toComponents { days, hours, minutes, seconds, _ -> "${days}d ${hours}h ${minutes}min ${seconds}sec" }}")
            }
        }
    }

}
