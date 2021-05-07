package space.okxjd.processiNG

import java.nio.file.LinkOption
import java.nio.file.Path as JPath
import java.io.File
import java.nio.charset.Charset
import java.util.logging.Logger
import kotlin.io.path.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.exists
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.isWritable
import kotlin.io.path.isReadable
import kotlin.io.path.listDirectoryEntries


@ExperimentalPathApi
open class Common {
    companion object {
        val logger: Logger = Logger.getLogger("")

        /** Тестовый набор слогов для определения кодировки текстового файла. */
        private val keyValues = listOf("аг", "ад", "аж", "аз", "ай", "ак", "ал", "ан", "ас", "ах",
            "ба", "бе", "би", "бо", "бу", "бы", "ва", "ве", "ви", "вн", "во", "вс", "ву", "вы", "вэ", "га", "гд",
            "ге", "ги", "гм", "го", "гу", "да", "дв", "де", "ди", "дл", "дн", "до", "ду", "ды", "ег", "ед", "ее",
            "еж", "ел", "ер", "ещ", "жа", "же", "жи", "жу", "жэ", "за", "зе", "зл", "зо", "зр", "зу", "зэ", "иб",
            "ив", "иг", "из", "ик", "ил", "им", "ио", "йо", "ис", "их", "иш", "ка", "ки", "ко", "кт", "ку", "ла",
            "ле", "ли", "ло", "лу", "ль", "лю", "ма", "ме", "ми", "мм", "мо", "му", "мы", "мэ", "мя", "на", "не",
            "ни", "но", "ну", "нэ", "ню", "об", "ог", "од", "ой", "ок", "ом", "он", "оп", "ор", "ос", "от", "ох",
            "па", "пе", "пи", "по", "пр", "пу", "пы", "ра", "ре", "ри", "ро", "ру", "ры", "ря", "са", "се", "си",
            "со", "ст", "су", "сы", "сэ", "та", "те", "ти", "то", "тр", "ту", "ты", "тю", "ув", "уг", "уж", "уз",
            "ум", "ур", "ус", "ух", "ую", "фе", "фи", "фо", "фу", "ха", "хе", "хи", "хм", "хо", "це", "ча", "че",
            "чи", "чт", "чу", "ша", "ше", "ши", "шо", "шт", "шу", "ща", "щи", "эй", "эр", "эт", "эх", "юг", "яв",
            "яд", "як", "ям", "яр", "ят")

        val wrkDir: String = System.getProperty("user.dir")
        var delimiterCSV = ';'
        const val quoteCharCSV = '"'
        const val escapeCharCSV = '\u0001'
        var sheetCnt: Int = 0
        var allRows: Int = 0
        var rowsLimit: Int = 65000
        var outFileCount: Int = 0

        lateinit var cfg: Map<String, String>
        internal lateinit var cfgAll: Map<String, Map<String, String>>

        lateinit var action: String
        private lateinit var inputDir: String
        private lateinit var outputDir: String
        lateinit var fullInputPath: JPath
        lateinit var fullOutputDir: String
       // lateinit var ext: String

        /** Загружает в **Common.cfg** конфигурацию по имени ключа из файла конфига. */
        fun load(configFile: JPath, key: String) {
            cfgAll = configLoad(configFile)
            cfg = cfgAll[key] ?: mapOf()
            action = cfg["action"] ?: ""
            inputDir = cfg["inputdir"] ?: "INPUT_DEFAULT"
            outputDir = cfg["outputdir"] ?: "OUTPUT_DEFAULT"
            fullInputPath = Path(wrkDir, inputDir)
            fullOutputDir = Path(wrkDir, outputDir).toString()
          //  ext = cfg["ext"]?.lowercase() ?: "csv"
            rowsLimit = try { if ((cfg["rows"]?.toInt() ?: 65000) > 1000000) 1000000
                else cfg["rows"]?.toInt() ?: 65000 } catch (e: NumberFormatException) {
                logger.info("${cfg["rows"].toString()} is not valid positive Int number; 65000 will be used."); 65000 }
            rowsLimit = if (rowsLimit > 0) rowsLimit else 65000
        }

        /** Загрузка файла конфига с диска и парсинг его содержимого.
         * Конфиг похож на *INI*, *CFG* или *properties* файлы.*/
        private fun configLoad(fileName: JPath): Map<String, Map<String, String>> {
            val iniMap: MutableMap<String, MutableMap<String, String>> = mutableMapOf()
            val iniFile = fileName.toFile().readLines()
            var tx0 = ""
            var tx1 = ""
            var tx2 = ""
            for (i in iniFile) {
                when {
                    i.trim() != "" -> {
                        when {
                            i.trim().startsWith('#') -> { tx0 = "" }
                            i.trim().startsWith('[') -> {
                                tx0 = i.trim().lowercase().slice(1 until i.length - 1)
                                iniMap[tx0] = mutableMapOf()
                            }
                            else -> {
                                tx1 = i.trim().lowercase().substringBefore(':').trim()
                                tx2 = i.trim().substringAfter(':').trim()
                                iniMap[tx0]?.set(tx1, tx2)
                            }
                        }
                    }
                }
            }
            iniMap.forEach { (k, v) ->
                if (k != "default") {
                    iniMap["default"]?.forEach { (k1, v1) ->
                        v.putIfAbsent(k1, v1)
                    }
                }
            }
            return iniMap.toMap()
        }

        /** Создать каталог */
        fun createDir(dir: String): JPath {
            return Path(dir).createDirectories()
        }

        /** Рекурсивно удалить каталог и все подкаталоги (включая непустые), без перехода по символьным ссылкам. */
        fun deleteDir(dir: String) {
            val t = Path(dir)
            if (t.exists() && t.isDirectory(LinkOption.NOFOLLOW_LINKS) && t.toFile().canWrite()) {
                t.toFile().deleteRecursively()
            }
        }

        /** Удалить один файл, без перехода по символьным ссылкам */
        fun deleteFile(file: String) {
            val t = Path(file)
            if (t.exists(LinkOption.NOFOLLOW_LINKS) && t.isRegularFile() && t.isWritable()) t.toFile().delete()
        }

        /** Возвращает список файлов или каталогов по запрошенному пути, без перехода по символьным ссылкам.
         * @param dir [java.nio.file.Path] до каталога, в котором будет проводиться поиск
         * @param type [String] тип объекта для поиска: "file" или "dir"
         * @param ext [String?] опционально - расширение файлов, которые будут включаться в итоговый список.
         * Если не задано - все файлы.
         * @return список объектов [java.nio.file.Path]
         * */
        fun getItemsList(dir: JPath, type: String, ext: String? = null): List<JPath> {
            val res = mutableListOf<JPath>()
            val resT = mutableListOf<JPath>()
            if (dir.exists() && dir.isDirectory(LinkOption.NOFOLLOW_LINKS)) {
                when (type) {
                    "dir" -> {
                        val td = dir.listDirectoryEntries("*")
                            .filter { it.isDirectory(LinkOption.NOFOLLOW_LINKS) }
                        res.addAll(td)
                        for (i in td) {
                            res.addAll(getItemsList(i, "dir"))
                        }
                    }
                    "file" -> {
                        resT.add(dir)
                        resT.addAll(getItemsList(dir, "dir"))
                        for (i in resT) {
                            res.addAll(
                                i.listDirectoryEntries(if (!ext.isNullOrBlank()) "*.${ext.lowercase()}" else "*")
                                    .filter { it.isRegularFile(LinkOption.NOFOLLOW_LINKS) }.toMutableList()
                            )
                        }
                    }
                }
            }
            return res.toList()
        }

        /** Служебная - для определения версии Excel по типу action */
        internal fun isToOldExcelVersion(action: String): Boolean {
            return when (action) {
                "csv2xls", "xlsx2xls" -> true
                else -> false
            }
        }

        /** Определяет кодировку текстового файла *"статистическим"* методом (выбирает из 3-х вариантов):
         * "utf-8", "windows-1251", системная кодировка по умолчанию [Charset.defaultCharset()] */
        fun getCharset(fileForTest: String): Charset {
            val cUTF8 = Charset.forName("utf-8")
            val cWin1251 = Charset.forName("windows-1251")
            var head0 = ""
            var head1 = ""
            var p0 = 0
            var p1 = 0
            if (Path(fileForTest).exists(LinkOption.NOFOLLOW_LINKS) && Path(fileForTest).isReadable() ) {
                val file0 = File(fileForTest).bufferedReader(cUTF8)
                val file1 = File(fileForTest).bufferedReader(cWin1251)
                for (i in 0..50) {
                    head0 += file0.readLine()
                    head1 += file1.readLine()
                }
                file0.close()
                file1.close()
                keyValues.forEach {
                    if (head0.contains(it, true)) p0 += 1
                    if (head1.contains(it, true)) p1 += 1
                }
            }
            return when {
                p0 > 0 && p1 > 0 -> if (p0 > p1) cUTF8 else cWin1251
                p0 > 0 && p1 == 0 -> cUTF8
                p0 == 0 && p1 > 0 -> cWin1251
                p0 == 0 && p1 == 0 -> Charset.defaultCharset()
                else -> Charset.defaultCharset()
            }
        }

        /** Определяет разделитель полей для CSV-файла. *"Статистическим"* методом. */
        fun getCsvDelimiter(fileForTest: String): Char {
            var head = ""
            if (Path(fileForTest).exists(LinkOption.NOFOLLOW_LINKS) && Path(fileForTest).isReadable() ) {
                val csvCharset = getCharset(fileForTest)
                val file = File(fileForTest).bufferedReader(csvCharset)
                for (i in 0..50) { head += file.readLine() }
                file.close()
            }
            return if (head.count{it == ';'} > head.count{it == ','}) ';' else ','
        }

    }
}


