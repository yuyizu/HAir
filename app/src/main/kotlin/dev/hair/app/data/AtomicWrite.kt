package dev.hair.app.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 先写临时文件再原子替换，避免进程中途被杀留下半个文件。 */
internal fun File.writeTextAtomically(text: String) {
    val tmp = File("$path.tmp")
    tmp.writeText(text)
    Files.move(tmp.toPath(), toPath(), StandardCopyOption.ATOMIC_MOVE)
}
