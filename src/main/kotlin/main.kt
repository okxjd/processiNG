package space.okxjd.processiNG

import com.github.doyaaaaaken.kotlincsv.client.KotlinCsvExperimental
import java.util.logging.LogManager
import java.util.logging.Logger
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


private const val appName: String = "ProcessiNG"
private val appVersion: String? = Thread.currentThread()
    .contextClassLoader
    .getResourceAsStream("build.txt")
    ?.readAllBytes()
    ?.decodeToString()

@ExperimentalTime
@ExperimentalPathApi
@KotlinCsvExperimental
fun doAction(args: Map<String, String>) {
    val logger = Logger.getLogger("")

    fun getList(): List<String> {
        val tt: MutableList<String> = mutableListOf()
        args["list"].toString().split(args["delimiter"].toString().trim()[0]).forEach { tt.add(it.trim()) }
        return tt.toList()
    }
    when (args["action"].toString().lowercase()) {
        "csv2xlsx", "csv2xls", "xls2xlsx", "xlsx2xls" -> { Converter().convert() }
        "split_xlsx_rows", "split_xlsx_mask" -> { Splitter().SplitXLSX().split() }
        "split_txt" -> { Splitter().SplitTXT().split() }
        "split_csv" -> { Splitter().SplitCSV().split() }
        "join_txt" -> { JoinFiles().JoinTxt().joinTxt() }
        "join_csv" -> { JoinFiles().JoinTxt().joinCsv() }
        "join_xlsx" -> { JoinFiles().JoinXlsx().join() }
        "create_dir" -> {
            val lst = getList()
            for (i in lst) {
                logger.info("CREATE DIR: $i")
                Common.createDir(Path(Common.wrkDir,i).toString())
            }
        }
        "delete_dir" -> {
            val lst = getList()
            lst.forEach { i ->
                logger.info("DELETE DIR: $i")
                Common.deleteDir(Path(Common.wrkDir,i).toString())
            }
        }
        "delete_file" -> {
            val lst = getList()
            lst.forEach { i ->
                logger.info("DELETE FILE: $i")
                Common.deleteFile(Path(Common.wrkDir, i).toString())
            }
        }
        "actionchain" -> {
            val lst = getList()
            logger.info("> $lst")
            lst.forEach { i ->
                logger.info(" > $i")
                doAction(Common.cfgAll[i] ?: mapOf())
            }
        }
        else -> {
            logger.info("Action ${args["action"]} is undefined.")
        }
    }
}

@ExperimentalTime
@ExperimentalPathApi
@KotlinCsvExperimental
fun main(args: Array<String>) {
    println("$appName $appVersion")
    if (args.isEmpty()) {
        println("WHAT ARE YOU WANT FROM ME !!!???")
        exitProcess(-1)
    }
    else {
        val defaultCfg = if (args.size < 2) "cfg/config.conf" else args[1]
        Common.load(Path(Common.wrkDir, defaultCfg), args[0].lowercase().trim())
        Common.createDir(Path(Common.wrkDir,"log").toString())
        val logger = Logger.getLogger("")
        val logCfg = Thread.currentThread().contextClassLoader.getResourceAsStream("logger.properties")
        LogManager.getLogManager().readConfiguration(logCfg)
        logger.info("CONFIG: ${args[0]} [action: ${Common.cfg["action"]}]")
        logger.info("START: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.YYYY HH:mm:ss"))}")
        doAction(Common.cfg)
        logger.info("STOP: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.YYYY HH:mm:ss"))}")
        logger.info("END")
        logger.info("-----")
        logger.info("")
    }
}
