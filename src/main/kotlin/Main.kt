import java.io.File
import java.net.URL
import java.util.*

fun main() {
    Crawler().crawl(120)
}

class Crawler {

    private val indexFile: File = File("./index.txt")
    private val outputDirPath = "./result"
    private val outputDirectory = File(outputDirPath)
    private var counter = 0

    init {
        if (indexFile.exists()) {
            indexFile.delete()
        }
        indexFile.createNewFile()
        if (outputDirectory.exists()) {
            outputDirectory.deleteRecursively()
        }
        outputDirectory.mkdir()
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
        val fileName = "$outputDirPath/$counter.txt"
        val file = File(fileName)
        file.createNewFile()
        file.writeText(content)
        indexFile.appendText("$counter: $url\n")
        println("writing $url to file number $counter. Absolute path: ${file.absolutePath}")
        counter++
        return file
    }

}