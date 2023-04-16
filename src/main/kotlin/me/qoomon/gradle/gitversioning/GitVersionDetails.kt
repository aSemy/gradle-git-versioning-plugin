package me.qoomon.gradle.gitversioning

import me.qoomon.gitversioning.commons.GitRefType
import me.qoomon.gradle.gitversioning.GitVersioningPluginConfig.RefPatchDescription

class GitVersionDetails(
    @JvmField
    val commit: String,
    @JvmField
    val refType: GitRefType,
    @JvmField
    val refName: String,
    @JvmField
    val patchDescription: RefPatchDescription,
)
