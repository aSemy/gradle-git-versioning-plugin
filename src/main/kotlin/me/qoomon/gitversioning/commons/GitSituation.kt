package me.qoomon.gitversioning.commons

import me.qoomon.gitversioning.commons.GitUtil.NO_COMMIT
import me.qoomon.gitversioning.commons.GitUtil.branch
import me.qoomon.gitversioning.commons.GitUtil.describe
import me.qoomon.gitversioning.commons.GitUtil.revTimestamp
import me.qoomon.gitversioning.commons.GitUtil.status
import me.qoomon.gitversioning.commons.GitUtil.tagsPointAt
import org.eclipse.jgit.errors.NoWorkTreeException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.property
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.*
import java.util.function.Supplier
import java.util.regex.Pattern
import javax.inject.Inject

abstract class GitSituation @Inject constructor(
    private val repository: Repository,
    private val providers: ProviderFactory,
    private val objects: ObjectFactory,
) {
    private val logger: Logger = Logging.getLogger(this::class.java)

    @JvmField
    val rootDirectory: File = getWorkTree(repository)

    private val head: ObjectId? = repository.resolve(Constants.HEAD)

    @JvmField
    val rev: String = if (head != null) head.name else NO_COMMIT

    private val timestamp: Provider<ZonedDateTime> = providers.provider {
        if (head != null) {
            revTimestamp(repository, head)
        } else {
            ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)
        }
    }
    private val branch: Property<String> =
        objects.property<String>().convention(providers.provider { branch(repository) })

    private var tags: Supplier<List<String>> =
        Lazy.by { if (head != null) tagsPointAt(head, repository) else emptyList() }
//    abstract val tags: ListProperty<String>

    private val clean: Supplier<Boolean> = Lazy.by { status(repository).isClean }
//    abstract val clean2: Property<Boolean>

    var describeTagPattern: Pattern = Pattern.compile(".*")
        set(value) {
            field = value
            description = Lazy.by { this.describe() }
        }

    private var description: Supplier<GitDescription> = Lazy.by { this.describe() }
//    abstract val description2: Property<GitDescription>


    /**
     * fixed version `repository.getWorkTree()`
     * handle worktrees as well
     *
     * @param repository
     * @return .git directory
     */
    @Throws(IOException::class)
    private fun getWorkTree(repository: Repository): File {
        return try {
            repository.workTree
        } catch (e: NoWorkTreeException) {
            val gitDirFile = File(repository.directory, "gitdir")
            if (gitDirFile.exists()) {
                val gitDirPath = Files.readAllLines(gitDirFile.toPath())[0]
                return File(gitDirPath).parentFile
            }
            throw e
        }
    }

    fun getTimestamp(): ZonedDateTime = timestamp.get()

    fun getBranch(): String? = branch.orNull

    val isDetached: Boolean
        get() = branch.orNull == null

    fun getTags(): List<String> = tags.get()

    fun isClean(): Boolean = clean.get()

//    fun getDescribeTagPattern(): Pattern = describeTagPattern

    fun getDescription(): GitDescription = description.get()

    // ----- initialization methods ------------------------------------------------------------------------------------

    @Throws(IOException::class)
    private fun describe(): GitDescription = describe(head, describeTagPattern, repository)


    @Throws(IOException::class)
      fun handleEnvironment(
        repository: Repository,
        overrideBranch: String?,
        overrideTag: String?,
        providedRef: String?,
    ) {
        // --- commandline arguments and environment variables

        var overrideBranch = overrideBranch
        var overrideTag = overrideTag
        if (overrideBranch != null || overrideTag != null) {
            overrideBranch =
                if (overrideBranch == null || overrideBranch.trim { it <= ' ' }.isEmpty()) {
                    null
                } else {
                    overrideBranch.trim { it <= ' ' }
                }
            setBranch(overrideBranch)
            overrideTag =
                if (overrideTag == null || overrideTag.trim { it <= ' ' }.isEmpty()) {
                    null
                } else {
                    overrideTag.trim { it <= ' ' }
                }
            setTags(listOfNotNull(overrideTag))
            return
        }

        if (providedRef != null) {
            require(providedRef.startsWith("refs/")) { "invalid provided ref $providedRef -  needs to start with refs/" }
            if (providedRef.startsWith("refs/tags/")) {
                setBranch(null)
                setTags(listOf(providedRef))
            } else {
                setBranch(providedRef)
                setTags(emptyList())
            }
            return
        }

        // --- try getting branch and tag situation from environment ---
        // skip if we are on a branch
        if (repository.branch == null) {
            return
        }

        // GitHub Actions support
        if (System.getenv("GITHUB_ACTIONS").equals("true", ignoreCase = true)) {
            if (System.getenv("GITHUB_SHA") != rev) {
                return
            }
            logger.lifecycle("gather git situation from GitHub Actions environment variable: GITHUB_REF")
            val githubRef = System.getenv("GITHUB_REF")
            logger.debug("  GITHUB_REF: $githubRef")
            if (githubRef.startsWith("refs/tags/")) {
                addTag(githubRef)
            } else {
                setBranch(githubRef)
            }
            return
        }

        // GitLab CI support
        if ("true".equals(System.getenv("GITLAB_CI"), ignoreCase = true)) {
            if (System.getenv("CI_COMMIT_SHA") != rev) {
                return
            }
            logger.lifecycle("gather git situation from GitLab CI environment variables: CI_COMMIT_BRANCH, CI_MERGE_REQUEST_SOURCE_BRANCH_NAME and CI_COMMIT_TAG")
            val commitBranch = System.getenv("CI_COMMIT_BRANCH")
            val commitTag = System.getenv("CI_COMMIT_TAG")
            val mrSourceBranch = System.getenv("CI_MERGE_REQUEST_SOURCE_BRANCH_NAME")
            logger.debug("  CI_COMMIT_BRANCH: $commitBranch")
            logger.debug("  CI_COMMIT_TAG: $commitTag")
            logger.debug("  CI_MERGE_REQUEST_SOURCE_BRANCH_NAME: $mrSourceBranch")
            commitBranch?.let { setBranch(it) } ?: (mrSourceBranch?.let { setBranch(it) }
                ?: commitTag?.let { addTag(it) })
            return
        }

        // Circle CI support
        if ("true".equals(System.getenv("CIRCLECI"), ignoreCase = true)) {
            if (System.getenv("CIRCLE_SHA1") != rev) {
                return
            }
            logger.lifecycle("gather git situation from Circle CI environment variables: CIRCLE_BRANCH and CIRCLE_TAG")
            val commitBranch = System.getenv("CIRCLE_BRANCH")
            val commitTag = System.getenv("CIRCLE_TAG")
            logger.debug("  CIRCLE_BRANCH: $commitBranch")
            logger.debug("  CIRCLE_TAG: $commitTag")
            commitBranch?.let { setBranch(it) } ?: commitTag?.let { addTag(it) }
            return
        }

        // Jenkins support
        if (System.getenv("JENKINS_HOME") != null && System.getenv("JENKINS_HOME").trim { it <= ' ' }
                .isNotEmpty()) {
            if (System.getenv("GIT_COMMIT") == rev) {
                return
            }
            logger.lifecycle("gather git situation from jenkins environment variables: BRANCH_NAME and TAG_NAME")
            val commitBranch = System.getenv("BRANCH_NAME")
            val commitTag = System.getenv("TAG_NAME")
            logger.debug("  BRANCH_NAME: $commitBranch")
            logger.debug("  TAG_NAME: $commitTag")
            if (commitBranch != null) {
                if (commitBranch == commitTag) {
                    addTag(commitBranch)
                } else {
                    setBranch(commitBranch)
                }
            } else commitTag?.let { addTag(it) }
            return
        }

    }

      fun setBranch(branch: String?) {
        logger.debug("override git branch with $branch")

        val finalBranch = if (branch != null) {
            require(!branch.startsWith("refs/tags/")) { "invalid branch ref$branch" }
            branch
                // support default branches (heads)
                .replaceFirst("^refs/heads/".toRegex(), "")
                // support other refs e.g. GitHub pull requests refs/pull/1000/head
                .replaceFirst("^refs/".toRegex(), "")
        } else {
            null
        }
        this.branch.set(finalBranch)
    }

      fun setTags(tags: List<String>) {
        logger.debug("override git tags with single tag $tags")
//        var tags = tags
//        Objects.requireNonNull(tags)
        val finalTags = tags.onEach { tag: String ->
            Objects.requireNonNull(tag)
            require(!(tag.startsWith("refs/") && !tag.startsWith("refs/tags/"))) { "invalid tag ref$tag" }
        }.map { tag: String -> tag.replaceFirst("^refs/tags/".toRegex(), "") }
//       tags
        this.tags = Supplier { finalTags }
    }

      fun addTag(tag: String) {
        logger.debug("add git tag $tag")

          require(!(tag.startsWith("refs/") && !tag.startsWith("refs/tags/"))) { "invalid tag ref$tag" }
          val finalTag = tag.replaceFirst("^refs/tags/".toRegex(), "")
          val currentTags = tags
          tags = Lazy.by {
              val tags: MutableList<String> = ArrayList(currentTags.get())
              tags.add(finalTag)
              tags
          }
    }
}
