package me.qoomon.gitversioning.commons

import java.io.Serializable

class GitDescription(
    val commit: String,
    val tag: String,
    val distance: Int,
) : Serializable {
    private val shortCommit = commit.substring(0, 7)
    override fun toString(): String = "$tag-$distance-g${shortCommit}"
}
