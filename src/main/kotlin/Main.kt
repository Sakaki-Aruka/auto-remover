package online.aruka.amountChecker

import com.akuleshov7.ktoml.file.TomlFileReader
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.ZonedDateTime
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

var FILES_LIMIT = 21
var DURATION_LIMIT = "3month"
var LOAD_DIR = "."

const val SETTINGS_FILE = "ac.toml"

@Serializable
private class Config(
    @SerialName("files-limit") val filesLimit: String = FILES_LIMIT.toString(),
    @SerialName("duration-limit") val durationLimit: String = DURATION_LIMIT,
    @SerialName("load-dir") val loadDir: String = LOAD_DIR
)

fun main() {

    /*
     * 実装リスト
     * - コンフィグファイルの読み込み（もしくはデフォルト設定の適用）
     *   - toml からの読み込み
     *   - ファイル数上限
     *   - 期間上限(year, month, week, day, hour, minute, second, milli)
     * - 指定されたディレクトリに存在するファイルのリストアップ
     * - ファイル数の確認（超過なら次に進む）
     * - 保存期間を超過したファイルを最終更新が古い順にソート
     * - 残りファイル数が指定された個数になるまで古いファイルを削除
     * - finally: 結果をログとして出力
     */

    if (Paths.get(SETTINGS_FILE).toFile().let { it.exists() && it.isFile && it.canRead() }) loadSettings()
    val files: Set<File> = getFiles(Paths.get(LOAD_DIR)) ?: run {
        System.err.println("invalid target directory")
        return
    }

    if (files.size <= FILES_LIMIT) {
        println("${files.size} files are there. (it <= $FILES_LIMIT)")
        return
    }

    val sortedFiles: MutableList<File> = getSortedFiles(files)
    val beforeDeletingFiles: List<String> = sortedFiles.map { it.name }
    deleteWithDurationLimit(sortedFiles)
    deleteWithAmount(sortedFiles)
    log(sortedFiles, beforeDeletingFiles)
}

private fun loadSettings() {
    val data: Config = TomlFileReader.decodeFromFile(serializer(), SETTINGS_FILE)

    data.filesLimit.let {
        it.takeIf{ l -> l.matches(Regex("[0-9]+")) }
            ?.let { i -> FILES_LIMIT = i.toInt() }
    }

    data.durationLimit.let {
        it.takeIf { l -> l.isNotBlank() && l.matches(Regex("([0-9]+(year|month|week|day|hour|minute|second|milli))*")) }
            ?.let { l -> DURATION_LIMIT = l }
    }

    data.loadDir.let {
        val dir: File = Paths.get(it).toFile()
        if (dir.exists() && dir.isDirectory && dir.canRead()) LOAD_DIR = it
    }
}

private fun getFiles(current: Path): Set<File>? {
    if (!current.isDirectory() || !current.exists() || current.toFile().listFiles() == null) return null
    return current.toFile().listFiles()?.toSet()
}

private fun getSortedFiles(files: Set<File>): MutableList<File> {
    val result: MutableList<File> = mutableListOf()
    val timetable: MutableSet<Long> = mutableSetOf()
    files.forEach { timetable.add(it.lastModified()) }
    timetable.sorted().forEach { t ->
        files.filter { f -> f.lastModified() == t }.forEach { f -> result.add(f) }
    }
    return result
}

private fun parseDuration(): Duration {
    val result: Duration = Duration.ZERO
    val matcher: Matcher = Pattern.compile("([0-9]+)(year|month|week|day|hour|minute|second|milli)").matcher(DURATION_LIMIT)
    matcher.find()
    matcher.results().forEach {
        val i: Long = it.group(1).toLong()
        val type: String = it.group(2)
        when (type) {
            "year" -> result.plusDays(365 * i)
            "month" -> result.plusDays(30 * i)
            "week" -> result.plusDays(7 * i)
            "day" -> result.plusDays(i)
            "hour" -> result.plusHours(i)
            "minute" -> result.plusMinutes(i)
            "second" -> result.plusSeconds(i)
            "milli" -> result.plusMillis(i)
            else -> {}
        }
    }
    return result
}

private fun deleteWithDurationLimit(files: MutableList<File>) {
    val deadline: ZonedDateTime = ZonedDateTime.now().minus(parseDuration())
    files.filter { it.lastModified() < deadline.toEpochSecond() }
        .forEach {
            it.delete()
            files.remove(it)
        }
}

private fun deleteWithAmount(files: MutableList<File>) {
    if (files.size <= FILES_LIMIT) return
    repeat(files.size - FILES_LIMIT) {
        files.first().let { f ->
            f.delete()
            files.remove(f)
        }
    }
}

private fun log(files: List<File>, beforeNames: List<String>) {
    if (files.size >= beforeNames.size) return
    beforeNames.filter { n -> !files.any { f -> f.name == n } }
        .forEach {
            println("Deleted: $it")
        }
    println("${beforeNames.size - files.size} files deleted.")
}