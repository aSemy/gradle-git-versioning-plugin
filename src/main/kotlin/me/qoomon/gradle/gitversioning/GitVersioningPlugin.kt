package me.qoomon.gradle.gitversioning

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import javax.inject.Inject

abstract class GitVersioningPlugin @Inject constructor(
    private val providers: ProviderFactory,
) : Plugin<Project> {

    /** for main logic see [GitVersioningPluginExtension.apply] */
    override fun apply(project: Project) {
        val extension = project.extensions.create<GitVersioningPluginExtension>("gitVersioning", project)
        with(extension) {
//            projectVersion.convention(providers.provider { project.version.toString() })
            projectProperties.putAll(providers.provider { project.properties })
        }
        project.tasks.register<VersionTask>("version")
    }
}
