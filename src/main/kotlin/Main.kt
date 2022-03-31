import com.github.demidko.aot.PartOfSpeech
import com.github.demidko.aot.WordformMeaning
import java.io.File
import java.net.URL
import java.util.*
import kotlin.NoSuchElementException
import kotlin.math.ln
import kotlin.math.sqrt

fun main() {
    SearchApp().start()
}

const val indexFilePath = "./index.txt"
val indexFile = File(indexFilePath)

const val resultDirectoryPath = "./result"
val resultDirectory = File(resultDirectoryPath)

const val wordsFilePath = "./words.txt"
val wordsFile = File(wordsFilePath)

const val lemmasFilePath = "./lemmas.txt"
val lemmasFile = File(lemmasFilePath)

const val indexesFilePath = "./inv_indexes.txt"
val indexesFile = File(indexesFilePath)

const val lemmasIndexesFilePath = "./inv_indexes_lemmas.txt"
val lemmasIndexesFile = File(lemmasIndexesFilePath)

const val wordsTfIdfDirectoryPath = "./words_tf_idf"
val wordsTfIdfDirectory = File(wordsTfIdfDirectoryPath)

const val lemmasTfIdfDirectoryPath = "./lemmas_tf_idf"
val lemmasTfIdfDirectory = File(lemmasTfIdfDirectoryPath)

class Crawler {

    private var counter = 0

    init {
        checkFileAndClean(indexFile)
        if (resultDirectory.exists()) {
            resultDirectory.deleteRecursively()
        }
        resultDirectory.mkdir()
        counter = 0
    }

    fun crawl(number: Int) {
        val baseUrl = "http://scpfoundation.net/scp-%03d"
        for (i in 2..number + 2) {
            val url = String.format(baseUrl, i)
            val content = try {
                getPage(url)
            } catch (e: Exception) {
                e.printStackTrace()
                continue
            }
            saveContentIntoFile(url, content)
        }
    }

    private fun getPage(url: String): String {
        val connection = URL(url).openConnection()
        val scanner = Scanner(connection.getInputStream())
        scanner.useDelimiter("\\Z")
        val content = scanner.next()
        scanner.close()
        return content
    }

    private fun saveContentIntoFile(url: String, content: String): File {
        val fileName = "$resultDirectoryPath/$counter.txt"
        val file = File(fileName)
        file.createNewFile()
        file.writeText(content)
        indexFile.appendText("$counter: $url\n")
        counter++
        return file
    }

}

class Lemmatizer {

    private val lemmasSet = mutableSetOf<LemmaPair>()

    init {
        checkFileAndClean(wordsFile)
        checkFileAndClean(lemmasFile)
    }

    fun start() {
        for (file in resultDirectory.listFiles() ?: emptyArray()) {
            analyseFile(file)
        }
        writeWordsToFiles()
    }

    private fun analyseFile(file: File) {
        val lemmas = FileReader.readWebPageFile(file)
        lemmasSet.addAll(lemmas)
    }

    private fun writeWordsToFiles() {
        for (lemma in lemmasSet) {
            lemmasFile.appendText("${lemma.lemma} ${lemma.word}\n")
            wordsFile.appendText(lemma.word)
            wordsFile.appendText("\n")
        }
    }
}

class Indexer {
    init {
        checkFileAndClean(indexesFile)
        checkFileAndClean(lemmasIndexesFile)
    }

    fun start() {
        val map = mutableMapOf<LemmaPair, MutableList<Int>>()
        val lemmasMap = mutableMapOf<String, MutableSet<Int>>()
        for (file in resultDirectory.listFiles() ?: emptyArray()) {
            val num = file.nameWithoutExtension.toInt()
            val words = FileReader.readWebPageFile(file)
            for (word in words) {
                map[word] = (map[word] ?: mutableListOf()).apply { add(num) }
                lemmasMap[word.lemma] = (lemmasMap[word.lemma] ?: mutableSetOf()).apply { add(num) }
            }
        }
        for (entry in map) {
            indexesFile.appendText("${entry.key.word} ${entry.value.joinToString(separator = " ")}\n")
        }
        for (lemEntry in lemmasMap) {
            lemmasIndexesFile.appendText("${lemEntry.key} ${lemEntry.value.joinToString(separator = " ")}\n")
        }

    }
}

class FileReader {

    companion object {

        fun readWebPageFile(file: File): Set<LemmaPair> {
            return readWebPageFileAsList(file).toSet()
        }

        fun readWebPageFileAsList(file: File): List<LemmaPair> {
            val result = mutableListOf<LemmaPair>()
            println("Reading from file ${file.name}")
            val scanner = Scanner(file)
            while (scanner.hasNext()) {
                val word = scanner.next().lowercase()
                if (!word.matches(Regex("[\"(]*[а-яА-Я]+[;,!?)\"]*"))) {
                    continue
                }
                word.replace(Regex("[;,!?()\"]"), "")
                val meanings = WordformMeaning.lookupForMeanings(word)
                val lemma = meanings.getOrNull(0)?.lemma
                if (unusedPartOfSpeech.contains(lemma?.partOfSpeech)) {
                    continue
                }
                lemma?.let {
                    result.add(LemmaPair(it.toString(), word))
                }
            }
            return result
        }

        fun readLemmasFile(): Set<LemmaPair> {
            println("Reading lemmas file")
            val result = mutableSetOf<LemmaPair>()
            val scanner = Scanner(lemmasFile)
            while (scanner.hasNextLine()) {
                val line = scanner.nextLine()
                val words = line.split(" ")
                val lemma = words.getOrNull(0) ?: continue
                val word = words.getOrNull(1) ?: continue
                result.add(LemmaPair(lemma, word))
            }
            return result
        }

        fun readIndexesFile(file: File): List<Index> {
            println("Reading indexes file ${file.name}")
            val result = mutableListOf<Index>()
            val scanner = Scanner(file)
            while (scanner.hasNextLine()) {
                val line = scanner.nextLine()
                val arr = line.split(" ")
                val word = arr.getOrNull(0) ?: continue
                val list = mutableListOf<Int>()
                for (i in 1 until arr.size) {
                    val num = arr[i].toInt()
                    list.add(num)
                }
                result.add(Index(word, list))
            }
            return result
        }

        fun readIdfFile(file: File): List<WordTfIdf> {
            println("Reading tf idf from file ${file.name}")
            val result = mutableListOf<WordTfIdf>()
            val scanner = Scanner(file)
            while (scanner.hasNextLine()) {
                val line = scanner.nextLine()
                val arr = line.split(" ")
                val word = arr.getOrNull(0) ?: continue
                val idf = arr.getOrNull(1)?.toDouble() ?: continue
                val tfIdf = arr.getOrNull(2)?.toDouble() ?: continue
                result.add(WordTfIdf(word, idf, tfIdf))
            }
            return result

        }

        fun readIndexfile(): Map<Int, String> {
            val result = mutableMapOf<Int, String>()
            val scanner = Scanner(indexFile)
            while (scanner.hasNextLine()) {
                val line = scanner.nextLine()
                val arr = line.split(": ")
                val num = arr.getOrNull(0)?.toInt() ?: throw IllegalArgumentException("АААААААААААА")
                val str = arr.getOrNull(1) ?: throw IllegalArgumentException("Я ТАК БОЛЬШЕ НЕ МОГУ")
                result[num] = str
            }
            return result
        }
    }
}

class BoolSearcher : BaseSearcher() {

    fun demo() {
        println("условия & зоны")
        println(boolWords("условия", "зоны", LogicType.AND))

        println("государства | сми")
        println(boolWords("государства", "сми", LogicType.OR))

        println("всех ! вероятность")
        println(boolWords("всех", "вероятность", LogicType.NOR))

    }

    private fun boolWords(w1: String, w2: String, type: LogicType): Set<Int> {
        val l1 = searchForWord(w1)
        val l2 = searchForWord(w2)
        return when (type) {
            LogicType.AND -> and(l1, l2)
            LogicType.OR -> or(l1, l2)
            LogicType.NOR -> nor(l1, l2)
        }
    }

    private fun and(l1: List<Int>, l2: List<Int>): Set<Int> {
        return l1.intersect(l2)
    }

    private fun or(l1: List<Int>, l2: List<Int>): Set<Int> {
        return l1.union(l2)
    }

    private fun nor(l1: List<Int>, l2: List<Int>): Set<Int> {
        return l1.subtract(l2)
    }

    enum class LogicType {
        AND, OR, NOR
    }
}

class StatsCounter {

    init {
        if (wordsTfIdfDirectory.exists()) {
            wordsTfIdfDirectory.deleteRecursively()
        }
        wordsTfIdfDirectory.mkdir()
        if (lemmasTfIdfDirectory.exists()) {
            lemmasTfIdfDirectory.deleteRecursively()
        }
        lemmasTfIdfDirectory.mkdir()
    }

    fun start() {
        val allLemmas = FileReader.readIndexesFile(lemmasIndexesFile)
        val allWords = FileReader.readIndexesFile(indexesFile)
        val files = resultDirectory.listFiles() ?: emptyArray()
        for (file in files) {
            val wordsMap = mutableMapOf<Index, Int>()
            val lemmasMap = mutableMapOf<Index, Int>()
            val fileNum = file.nameWithoutExtension.toInt()
            val fileWords = FileReader.readWebPageFileAsList(file)
            val tfWords = allWords.filter { it.places.contains(fileNum) }

            for (wordIndex in tfWords) {
                val compatible = fileWords.filter { it.word == wordIndex.word }
                wordsMap[wordIndex] = compatible.size

                val lemma = allLemmas.find { it.word == compatible[0].lemma }
                    ?: throw NoSuchElementException("Может под диваном лежит?")
                lemmasMap[lemma] = (lemmasMap[lemma] ?: 0) + compatible.size
            }
            val wordsTfFile = File("$wordsTfIdfDirectoryPath/$fileNum.txt")
            wordsTfFile.createNewFile()

            val lemmasTfFile = File("$lemmasTfIdfDirectoryPath/$fileNum.txt")
            lemmasTfFile.createNewFile()

            fun writeMapIntoFile(map: Map<Index, Int>, tfFile: File) {
                for (entry in map) {
                    val tf = entry.value.toDouble() / fileWords.size.toDouble()
                    val idf = ln(files.size.toDouble() / entry.key.places.size.toDouble())
                    tfFile.appendText("${entry.key.word} $idf ${tf * idf}\n")
                }
            }

            writeMapIntoFile(wordsMap, wordsTfFile)
            writeMapIntoFile(lemmasMap, lemmasTfFile)

        }
    }
}

class SearchApp {
    fun start() {
        val searcher = VectorSearcher()
        while (true) {
            println("Готов к поиску. Что ищем?")
            val str = readLine() ?: continue

            try {
                val res = searcher.search(str)
                if (res.isEmpty()) {
                    println("Ничего не удалось найти. Давайте попробуем еще раз")
                    continue
                }
                println("Вот что мы нашли:")
                for (url in res.reversed()) {
                    println(url)
                }
            }catch (e: Exception) {
                println("Что то пошло не так. Давайте попробуем еще раз?")
                continue
            }
        }
    }
}

class VectorSearcher : BaseSearcher() {

    val allUrls = FileReader.readIndexfile()

    fun search(query: String): List<String> {
        val q = formatQuery(query)

        val filesIndexes = mutableSetOf<Int>()
        for (word in q) {
            filesIndexes.addAll(searchForWord(word, lemmasIndexesFile))
        }

        val filesVectorsMap = mutableMapOf<Int, List<Double>>()
        val vectorizer = Vectorizer()
        for (num in filesIndexes) {
            filesVectorsMap[num] = vectorizer.vectorise(num)
        }
        val qVector = vectorizer.vectorise(q)

        val filesSimilarityMap = mutableMapOf<Int, Double>()
        val vectorsComparer = VectorsComparer()
        for (num in filesIndexes) {
            filesSimilarityMap[num] =
                vectorsComparer.calculateVectorsSimilarity(
                    qVector,
                    filesVectorsMap[num] ?: throw NoSuchElementException("А че где...")
                )
        }
        val sortedNums = filesSimilarityMap.toList().sortedBy { (_, value) -> value }.map { it.first }
        val urls = sortedNums.mapNotNull { allUrls[it] }
        return urls
    }

    private fun formatQuery(query: String): List<String> {
        val arr = query.split(" ")
        val res = mutableListOf<String>()
        for (word in arr) {
            word.replace(Regex("[;,!?()\"]"), "")
            val meanings = WordformMeaning.lookupForMeanings(word)
            val lemma = meanings.getOrNull(0)?.lemma ?: continue
            if (unusedPartOfSpeech.contains(lemma.partOfSpeech)) {
                continue
            }
            lemma?.let {
                res.add(it.toString())
            }
        }
        if (res.isEmpty()) throw IllegalArgumentException("Все совсем не так как мы ожидали")
        return res
    }
}

open class BaseSearcher {

    /**
     *  @param file indexesFile format only!
     */
    protected fun searchForWord(word: String, file: File? = null): List<Int> {
        val scanner = Scanner(file ?: indexesFile)
        while (scanner.hasNextLine()) {
            val line = scanner.nextLine()
            if (line.startsWith(word)) {
                val arr = line.split(" ")
                val list = mutableListOf<Int>()
                for (i in 1 until arr.size) {
                    val num = arr[i].toInt()
                    list.add(num)
                }
                return list
            }

        }
        throw NoSuchElementException("По карманам поищи")
    }
}

class VectorsComparer {
    fun calculateVectorsSimilarity(l1: List<Double>, l2: List<Double>): Double {
        val length = l1.size
        if (l2.size != length) throw IllegalArgumentException("Вы эти колоды в киосках заправляете что ли?")
        var sm = 0.0
        var s1 = 0.0
        var s2 = 0.0
        for (numIndexed in l1.withIndex()) {
            sm += (numIndexed.value * l2[numIndexed.index])
            s1 += numIndexed.value
            s2 += l2[numIndexed.index]
        }
        val sim = sm / (sqrt(s1) * sqrt(s2))
        return sim
    }
}

class Vectorizer {

    val lemmas = mutableListOf<String>()
    val idfs = mutableMapOf<String, Double>()

    init {
        val wordPairs = FileReader.readLemmasFile()
        val _lemmas = wordPairs.map { it.lemma }.distinct()
        lemmas.addAll(_lemmas)
    }

    fun vectorise(file: File): List<Double> {
        val list = MutableList(lemmas.size) { 0.0 }
        val wtfList = FileReader.readIdfFile(file)
        for (wtf in wtfList) {
            idfs[wtf.word] = wtf.idf
            list[lemmas.indexOf(wtf.word)] = wtf.tf_idf
        }
        return list
    }

    fun vectorise(fileNum: Int) = vectorise(File("$lemmasTfIdfDirectoryPath/$fileNum.txt"))

    fun vectorise(queryLemmasList: List<String>): List<Double> {
        val list = MutableList(lemmas.size) { 0.0 }
        for (word in queryLemmasList) {
            val tf = queryLemmasList.count { it == word }.toDouble() / queryLemmasList.size.toDouble()
            val idf = idfs[word] ?: continue
            val tfIdf = tf * idf
            list[lemmas.indexOf(word)] = tfIdf
        }
        if (list.isEmpty()) throw IllegalArgumentException("Не нашел ни одного совпадения")
        return list
    }

    fun clear() {
        idfs.clear()
    }

}

fun checkFileAndClean(file: File) {
    if (file.exists()) {
        file.delete()
    }
    file.createNewFile()
}

val unusedPartOfSpeech = listOf(
    PartOfSpeech.Union,
    PartOfSpeech.Numeral,
    PartOfSpeech.OrdinalNumber,
    PartOfSpeech.Pretext,
    PartOfSpeech.Particle,
)

data class LemmaPair(val lemma: String, val word: String)

data class Index(val word: String, val places: List<Int>)

data class WordTfIdf(val word: String, val idf: Double, val tf_idf: Double)