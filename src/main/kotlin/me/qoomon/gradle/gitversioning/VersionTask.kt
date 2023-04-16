package me.qoomon.gradle.gitversioning

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.util.concurrent.Callable

abstract class VersionTask : DefaultTask() {

    private val projectVersion = project.provider(Callable { project.version })

    @TaskAction
    fun printProjectVersion() {
        println(projectVersion.get().toString())
    }
}
