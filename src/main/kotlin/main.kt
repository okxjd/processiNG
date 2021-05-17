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
import java.util.Locale


private const val appName: String = "ProcessiNG"
private val appVersion: String? = Thread.currentThread()
    .contextClassLoader
    .getResourceAsStream("build.txt")
    ?.readAllBytes()
    ?.decodeToString()

@ExperimentalTime
@ExperimentalPathApi
@KotlinCsvExperimental
fun doAction(act: String) {
    val logger = Logger.getLogger("")

    fun getList(): List<String> {
        val tt: MutableList<String> = mutableListOf()
        Common.cfgAll[act]?.get("list").toString().split(Common.cfgAll[act]?.get("delimiter").toString().trim()[0]).forEach { tt.add(it.trim()) }
        return tt.toList()
    }

    when (Common.cfgAll[act]?.get("action").toString().lowercase(Locale.getDefault())) {
        "csv2xlsx", "csv2xls", "xls2xlsx", "xlsx2xls" -> { Converter(act).convert() }
        "split_txt" -> { Splitter(act).SplitTXT().split() }
        "split_csv" -> { Splitter(act).SplitCSV().split() }
        "split_xlsx_rows" -> { Splitter(act).SplitXLSX().split() }
        "join_txt" -> { JoinFiles(act).JoinTxt().joinTxt() }
        "join_csv" -> { JoinFiles(act).JoinTxt().joinCsv() }
        "join_xlsx" -> { JoinFiles(act).JoinXlsx().join() }
        "create_dir" -> {
            val lst = getList()
            for (i in lst) {
                logger.info("   CREATE DIR: $i")
                Common.createDir(Path(Common.wrkDir,i).toString())
            }
        }
        "delete_dir" -> {
            val lst = getList()
            lst.forEach { i ->
                logger.info("   DELETE DIR: $i")
                Common.deleteDir(Path(Common.wrkDir,i).toString())
            }
        }
        "delete_file" -> {
            val lst = getList()
            lst.forEach { i ->
                logger.info("   DELETE FILE: $i")
                Common.deleteFile(Path(Common.wrkDir, i).toString())
            }
        }
        "actionchain" -> {
            val lst = getList()
            logger.info("> $lst")
            lst.forEach { i ->
                logger.info(" > $i")
                doAction(i)
            }
        }
        else -> {
            logger.info("Action ${Common.cfgAll[act]?.get("action")} is undefined.")
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
        val arg = args[0].lowercase(Locale.getDefault()).trim()

        // TODO тут д.б. проверки arg на безопасность, я помню

        Common.load(Path(Common.wrkDir, defaultCfg), arg)
        Common.createDir(Path(Common.wrkDir,"log").toString())
        val logger = Logger.getLogger("")
        val logCfg = Thread.currentThread().contextClassLoader.getResourceAsStream("logger.properties")
        LogManager.getLogManager().readConfiguration(logCfg)
        logger.info("CONFIG: $arg [action: ${Common.cfgAll[arg]?.get("action")}]")
        logger.info("START: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.YYYY HH:mm:ss"))}")
        doAction(arg)
        logger.info("STOP: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.YYYY HH:mm:ss"))}")
        logger.info("END")
        logger.info("-----")
        logger.info("")
    }
}
