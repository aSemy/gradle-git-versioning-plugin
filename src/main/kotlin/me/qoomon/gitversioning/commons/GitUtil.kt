package me.qoomon.gitversioning.commons

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.Status
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.regex.Pattern

object GitUtil {
    const val NO_COMMIT = "0000000000000000000000000000000000000000"

    @JvmStatic
    @Throws(GitAPIException::class)
    fun status(repository: Repository?): Status {
        return Git.wrap(repository).status().call()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun branch(repository: Repository): String? {
        val branch = repository.branch
        return if (ObjectId.isId(branch)) null else branch
    }

    @JvmStatic
    @Throws(IOException::class)
    fun tagsPointAt(revObjectId: ObjectId?, repository: Repository): List<String> {
        return reverseTagRefMap(repository).getOrDefault(revObjectId, emptyList())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun describe(revObjectId: ObjectId?, tagPattern: Pattern, repository: Repository): GitDescription {
        if (revObjectId == null) {
            return GitDescription(NO_COMMIT, "root", 0)
        }
        val objectIdListMap = reverseTagRefMap(repository)
        RevWalk(repository).use { walk ->
            walk.isRetainBody = false
            walk.isFirstParent = true
            walk.markStart(walk.parseCommit(revObjectId))
            val walkIterator: Iterator<RevCommit> = walk.iterator()
            var depth = 0
            while (walkIterator.hasNext()) {
                val rev = walkIterator.next()

                val matchingTag = objectIdListMap[rev]
                    ?.firstOrNull { tag -> tagPattern.matcher(tag).matches() }

                if (matchingTag != null) {
                    return GitDescription(revObjectId.name, matchingTag, depth)
                }

                depth++
            }
            check(!isShallowRepository(repository)) { "couldn't find matching tag in shallow git repository" }
            return GitDescription(revObjectId.name, "root", depth)
        }
    }

    fun isShallowRepository(repository: Repository): Boolean {
        return File(repository.directory, "shallow").isFile
    }

    @Throws(IOException::class)
    fun tags(repository: Repository): List<Ref> {
        return repository.refDatabase.getRefsByPrefix(Constants.R_TAGS)
            // .sorted(TagComparator(repository)) // TODO may can be removed
            .toList()
    }

    @Throws(IOException::class)
    fun reverseTagRefMap(repository: Repository): Map<ObjectId, List<String>> {
        val tagComparator = TagComparator(repository)

        return tags(repository).groupBy { r ->
            try {
                val peel = repository.refDatabase.peel(r)
                if (peel.peeledObjectId != null) peel.peeledObjectId else peel.objectId
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }.mapValues { (_, refs) ->
            refs
                .sortedWith(tagComparator)
                .map { ref -> Repository.shortenRefName(ref.name) }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun revTimestamp(repository: Repository, rev: ObjectId?): ZonedDateTime {
        val commitTime = Instant.ofEpochSecond(repository.parseCommit(rev).commitTime.toLong())
        return ZonedDateTime.ofInstant(commitTime, ZoneOffset.UTC)
    }
}
