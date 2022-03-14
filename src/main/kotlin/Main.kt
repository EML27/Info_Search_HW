import com.github.demidko.aot.PartOfSpeech
import com.github.demidko.aot.WordformMeaning
import java.io.File
import java.net.URL
import java.util.*

fun main() {
    Lemmatizer().start()
}

const val resultDirectoryPath = "./result"
val resultDirectory = File(resultDirectoryPath)

const val wordsFilePath = "./words.txt"
val wordsFile = File(wordsFilePath)

const val lemmasFilePath = "./lemmas.txt"
val lemmasFile = File(lemmasFilePath)

class Crawler {

    private val indexFile: File = File("./index.txt")
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
        println("reading from $url. Content length: ${content.length}")
        return content
    }

    private fun saveContentIntoFile(url: String, content: String): File {
        val fileName = "$resultDirectoryPath/$counter.txt"
        val file = File(fileName)
        file.createNewFile()
        file.writeText(content)
        indexFile.appendText("$counter: $url\n")
        println("writing $url to file number $counter. Absolute path: ${file.absolutePath}")
        counter++
        return file
    }

}

class Lemmatizer {

    private val wordsSet = mutableSetOf<String>()
    private val lemmasSet = mutableSetOf<Pair<String, String>>()

    private val unusedPartOfSpeech = listOf(
        PartOfSpeech.Union,
        PartOfSpeech.Numeral,
        PartOfSpeech.OrdinalNumber,
        PartOfSpeech.Pretext,
        PartOfSpeech.Particle,
    )

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
        val scanner = Scanner(file)
        while (scanner.hasNext()) {
            val word = scanner.next().lowercase()
            if (!word.matches(Regex("[\"(]*[a-zA-Zа-яА-Я]+[;,!?)\"]*"))) {
                continue
            }
            word.replace(Regex("[;,!?()\"]"), "")
            val meanings = WordformMeaning.lookupForMeanings(word)
            val lemma = meanings.getOrNull(0)?.lemma
            if (unusedPartOfSpeech.contains(lemma?.partOfSpeech)) {
                continue
            }
            lemma?.let {
                wordsSet.add(word)
                lemmasSet.add(Pair(it.toString(), word))
            }
        }
    }

    private fun writeWordsToFiles() {
        for (word in wordsSet) {
            wordsFile.appendText(word)
            wordsFile.appendText("\n")
        }
        for (lemma in lemmasSet) {
            lemmasFile.appendText("${lemma.first} ${lemma.second}\n")
        }
    }
}

fun checkFileAndClean(file: File) {
    if (file.exists()) {
        file.delete()
    }
    file.createNewFile()
}