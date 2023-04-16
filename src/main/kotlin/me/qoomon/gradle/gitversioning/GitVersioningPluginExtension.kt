package me.qoomon.gradle.gitversioning

import me.qoomon.gitversioning.commons.GitRefType
import me.qoomon.gitversioning.commons.GitSituation
import me.qoomon.gitversioning.commons.GitSituation.Companion.newGitSituation
import me.qoomon.gitversioning.commons.StringUtil.patternGroupValues
import me.qoomon.gitversioning.commons.StringUtil.patternGroups
import me.qoomon.gitversioning.commons.StringUtil.substituteText
import me.qoomon.gradle.gitversioning.GitVersioningPluginConfig.PatchDescription
import me.qoomon.gradle.gitversioning.GitVersioningPluginConfig.RefPatchDescription
import me.qoomon.gradle.gitversioning.GitVersioningPluginExtension.Companion.increase
import me.qoomon.gradle.gitversioning.GitVersioningPluginExtension.Companion.matchVersion
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.ex.ConfigurationException
import org.apache.maven.artifact.versioning.DefaultArtifactVersion
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.ProjectLayout
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.newInstance
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.io.StringWriter
import java.nio.file.Files
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

abstract class GitVersioningPluginExtension(
    @Deprecated("remove, try to avoid using project during task-exec")
    private val project: Project,
    private val objects: ObjectFactory,
    private val providers: ProviderFactory,
    private val layout: ProjectLayout,
) {
    private val logger = Logging.getLogger(GitVersioningPluginExtension::class.java)

    //    abstract val projectVersion: Property<String>
    abstract val projectProperties: MapProperty<String, Any?>

    private var config: GitVersioningPluginConfig? = null

    var gitVersionDetails: GitVersionDetails? = null
    abstract val globalFormatPlaceholderMap: MapProperty<String, String>

    @Throws(IOException::class)
    fun apply(action: Action<GitVersioningPluginConfig>) {
        val config = objects.newInstance(GitVersioningPluginConfig::class)
        action.execute(config)
        apply(config)
    }

    @Throws(IOException::class)
    fun apply(config: GitVersioningPluginConfig) {
        this.config = config
        normalizeConfig(config)
        apply()
    }

    @Throws(IOException::class)
    private fun apply() {
        // check if extension is disabled by command option
        val commandOptionDisable = getCommandOption(OPTION_NAME_DISABLE)
        if (commandOptionDisable != null) {
            val disabled = java.lang.Boolean.parseBoolean(commandOptionDisable)
            if (disabled) {
                logger.warn("skip - versioning is disabled by command option")
                return
            }
        } else {
            // check if extension is disabled by config option
            if (config!!.disable) {
                logger.warn("skip - versioning is disabled by config option")
                return
            }
        }
        val gitSituation = getGitSituation(layout.projectDirectory.asFile)
        if (gitSituation == null) {
            logger.warn("skip - project is not part of a git repository")
            return
        }
        if (logger.isDebugEnabled) {
            logger.debug("git situation:")
            logger.debug("  root directory: " + gitSituation.rootDirectory)
            logger.debug("  head commit: " + gitSituation.rev)
            logger.debug("  head commit timestamp: " + gitSituation.getTimestamp())
            logger.debug("  head branch: " + gitSituation.getBranch())
//            logger.debug("  head tags: " + gitSituation.getTags())
            logger.debug("  head description: " + gitSituation.getDescription())
        }

        // determine git version details
        gitVersionDetails = getGitVersionDetails(gitSituation, config)
        if (gitVersionDetails == null) {
            logger.warn(
                """
                    skip - no matching ref configuration and no rev configuration defined
                    git refs:
                      branch: ${gitSituation.getBranch()}
                    defined ref configurations:"
                """.trimIndent()
            )
//                      tags: ${gitSituation.getTags()}
            config!!.refs.list.forEach { ref: RefPatchDescription ->
                logger.warn("  ${ref.type.name.padEnd(6)} - pattern: ${ref.pattern}")
            }
            return
        }
        logger.lifecycle("matching ref: ${gitVersionDetails!!.refType.name} - ${gitVersionDetails!!.refName}")
        val patchDescription = gitVersionDetails!!.patchDescription
        logger.lifecycle("ref configuration: ${gitVersionDetails!!.refType.name} - pattern: ${patchDescription.pattern}")
        if (patchDescription.describeTagPattern != null) {
            logger.lifecycle("  describeTagPattern: ${patchDescription.describeTagPattern}")
            val patchDescribeTagPattern = patchDescription.getDescribeTagPattern()
            if (patchDescribeTagPattern != null) {
                gitSituation.describeTagPattern = patchDescribeTagPattern
            }
        }
        if (patchDescription.version != null) {
            logger.lifecycle("  version: ${patchDescription.version}")
        }
        if (patchDescription.properties_.isNotEmpty()) {
            logger.lifecycle("  properties:")
            patchDescription.properties_.forEach { (key: String, value: String) ->
                logger.lifecycle("    $key: $value")
            }
        }
        val updateGradleProperties = getUpdateGradlePropertiesOption(patchDescription)
        logger.lifecycle("  updateGradleProperties: $updateGradleProperties")
        globalFormatPlaceholderMap.putAll(
            generateGlobalFormatPlaceholderMap(
                providers = providers,
                objects = objects,
                gitSituation = gitSituation,
                gitVersionDetails = gitVersionDetails!!,
                projectProperties = projectProperties,
            )
        )
        val gitProjectProperties = generateGitProjectProperties(objects, gitSituation, gitVersionDetails!!)
        logger.lifecycle("")
        project.allprojects.forEach { project: Project ->
            val projectVersion = project.version.toString()
            val originalProjectVersion = providers.provider { projectVersion }
            val versionFormat = patchDescription.version
            if (versionFormat != null) {
                updateVersion(originalProjectVersion, versionFormat)
                logger.lifecycle("project version: $projectVersion")
            }
            val propertyFormats = patchDescription.properties_
            if (propertyFormats.isNotEmpty()) {
                updatePropertyValues(project, propertyFormats, originalProjectVersion)
            }
            addGitProjectProperties(project, gitProjectProperties)
            if (updateGradleProperties) {
                val gradleProperties = project.file("gradle.properties")
                if (gradleProperties.exists()) {
                    updateGradlePropertiesFile(gradleProperties, project)
                }
            }
        }
    }

    // ---- project processing -----------------------------------------------------------------------------------------
    private fun updateVersion(projectVersion: Provider<String>, versionFormat: String) {
        val gitProjectVersion = getGitVersion(versionFormat, projectVersion)
//        logger.info("set version to $gitProjectVersion")
        logger.lifecycle("set version to $gitProjectVersion")
        project.version = gitProjectVersion.orNull ?: "undefined"
//        project.version = object {
//            override fun toString(): String = gitProjectVersion.orNull ?: "undefined"
//        }
    }

    private fun updatePropertyValues(
        project: Project,
        propertyFormats: Map<String, String>,
        originalProjectVersion: Provider<String>
    ) {
        var logHeader = true
        // properties section
        for ((projectPropertyName, projectPropertyValue) in project.properties) {
            val propertyFormat = propertyFormats[projectPropertyName]
            if (propertyFormat != null) {
                if (projectPropertyValue == null || projectPropertyValue is String) {
                    val gitPropertyValue = getGitPropertyValue(
                        propertyFormat = propertyFormat,
                        originalValue = projectPropertyValue?.toString(),
                        projectVersion = originalProjectVersion
                    )
                    if (gitPropertyValue != projectPropertyValue) {
                        if (logHeader) {
                            logger.lifecycle("properties:")
                            logHeader = false
                        }
                        logger.lifecycle("  $projectPropertyName: $gitPropertyValue")
                        project.setProperty(projectPropertyName, gitPropertyValue)
                    }
                } else {
                    logger.warn(
                        "Can not update property $projectPropertyName. Expected value type is String, but was ${projectPropertyValue::class.qualifiedName}"
                    )
                }
            }
        }
    }

    private fun addGitProjectProperties(project: Project, gitProjectProperties: MapProperty<String, String>) {
        val extraProperties = project.extensions.extraProperties
        gitProjectProperties.get().forEach { (name: String, value: String?) -> extraProperties[name] = value }
    }

    private fun updateGradlePropertiesFile(gradleProperties: File, project: Project) {
        // read existing gradle.properties
        val gradlePropertiesConfig = PropertiesConfiguration()
        try {
            FileReader(gradleProperties).use { reader -> gradlePropertiesConfig.read(reader) }
        } catch (e: IOException) {
            throw RuntimeException(e)
        } catch (e: ConfigurationException) {
            throw RuntimeException(e)
        }

        // handle version
        if (gradlePropertiesConfig.containsKey("version")) {
            val gradlePropertyVersion = gradlePropertiesConfig.getProperty("version")
            val projectVersion = project.version
            if (projectVersion != gradlePropertyVersion) {
                gradlePropertiesConfig.setProperty("version", projectVersion)
            }
        }

        // handle properties
        val projectProperties = project.properties
        gitVersionDetails!!.patchDescription.properties_.forEach { (key: String, _) ->
            if (gradlePropertiesConfig.containsKey(key)) {
                val gradlePropertyValue = gradlePropertiesConfig.getProperty(key)
                val projectPropertyValue = projectProperties[key]
                if (projectPropertyValue != gradlePropertyValue) {
                    gradlePropertiesConfig.setProperty(key, projectPropertyValue)
                }
            }
        }
        try {
            StringWriter(512).use { writer ->
                gradlePropertiesConfig.write(writer)
                val gitVersionedGradlePropertiesBytes = writer.toString().toByteArray()
                val existingGradlePropertiesBytes = Files.readAllBytes(gradleProperties.toPath())
                // only write if there are changes
                if (!Arrays.equals(gitVersionedGradlePropertiesBytes, existingGradlePropertiesBytes)) {
                    Files.write(gradleProperties.toPath(), gitVersionedGradlePropertiesBytes)
                }
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        } catch (e: ConfigurationException) {
            throw RuntimeException(e)
        }
    }

    // ---- versioning -------------------------------------------------------------------------------------------------
    @Throws(IOException::class)
    private fun getGitSituation(executionRootDirectory: File): GitSituation? {
        val repositoryBuilder = FileRepositoryBuilder().findGitDir(executionRootDirectory)
        if (repositoryBuilder.gitDir == null) {
            return null
        }
        val repository = repositoryBuilder.build()
        return objects.newGitSituation(
            repository,
            overrideBranch = getCommandOption(OPTION_NAME_GIT_BRANCH),
            overrideTag = getCommandOption(OPTION_NAME_GIT_TAG),
            providedRef = getCommandOption(OPTION_NAME_GIT_REF),
        )
    }

    private fun getGitVersion(versionFormat: String, projectVersion: Provider<String>): Provider<String> {
        val placeholderMap: MapProperty<String, String> = generateFormatPlaceholderMap(projectVersion)
        return placeholderMap.map { placeholders ->
            println(placeholders)
            val sub = substituteText(versionFormat, placeholders)
            val slug = slugify(sub)
            println("substituted versionFormat:$versionFormat into $sub, slug:$slug")
            slug
        }
//        val substituted = providers.provider { substituteText(versionFormat, placeholderMap) }
//        return substituted.map { slugify(it) }
    }

    private fun getGitPropertyValue(
        propertyFormat: String,
        originalValue: String?,
        projectVersion: Provider<String>
    ): Provider<String> {
        val placeholderMap: MapProperty<String, String> = generateFormatPlaceholderMap(projectVersion)
        placeholderMap.put("value", originalValue ?: "")
        return placeholderMap.map { placeholders ->
            substituteText(propertyFormat, placeholders)
        }
//        return providers.provider { substituteText(propertyFormat, placeholderMap) }
    }

    private fun generateFormatPlaceholderMap(projectVersion: Provider<String>): MapProperty<String, String> {
        val placeholderMap: MapProperty<String, String> = objects.mapProperty()
        placeholderMap.putAll(globalFormatPlaceholderMap)

        placeholderMap.put("version", projectVersion)
        val projectVersionMatcher = projectVersion.map { matchVersion(it) }
        placeholderMap.put("version.core", projectVersionMatcher.map { it.group("core") ?: "0.0.0" })

        val majorVersion = projectVersionMatcher.map { it.group("major") ?: "0" }
        placeholderMap.put("version.major", majorVersion)
        placeholderMap.put("version.major.next", majorVersion.map { increase(it, 1) })

        val minorVersion = projectVersionMatcher.map { it.group("minor") ?: "0" }
        placeholderMap.put("version.minor", minorVersion)
        placeholderMap.put("version.minor.next", minorVersion.map { increase(it, 1) })

        val patchVersion = projectVersionMatcher.map { it.group("patch") ?: "0" }
        placeholderMap.put("version.patch", patchVersion)
        placeholderMap.put("version.patch.next", patchVersion.map { increase(it, 1) })

        val versionLabel = projectVersionMatcher.map { it.group("label") ?: "" }
        placeholderMap.put("version.label", versionLabel)
        placeholderMap.put("version.label.prefixed", versionLabel.map { label ->
            if (label.isNotEmpty()) "-$label" else ""
        })

        // deprecated
        placeholderMap.put("version.release", projectVersion.map { it.replaceFirst("-.*$".toRegex(), "") })
        val projectVersionPattern = config!!.projectVersionPattern()
        if (projectVersionPattern != null) {
            // ref pattern groups
            placeholderMap.putAll(
                projectVersion.map { v ->
                    patternGroupValues(projectVersionPattern, v).mapKeys { (k, _) -> "version.$k" }
                }
            )
//            patternGroupValues(projectVersionPattern, projectVersion).forEach { (groupName, value) ->
//                placeholderMap.put("version.$groupName", providers.provider { value })
//            }
        }
        return placeholderMap
    }


    // ---- configuration ----------------------------------------------------------------------------------------------
    private fun normalizeConfig(config: GitVersioningPluginConfig) {
        // consider global config
        val patchDescriptions: MutableList<PatchDescription> = ArrayList(config.refs.list)
        config.rev?.let { patchDescriptions.add(it) }
        for (patchDescription in patchDescriptions) {
            if (patchDescription.describeTagPattern == null) {
                patchDescription.describeTagPattern = config.describeTagPattern
            }
            if (patchDescription.updateGradleProperties == null) {
                patchDescription.updateGradleProperties = config.updateGradleProperties
            }
        }
    }

    private fun getCommandOption(name: String): String? {
        var value = System.getProperty(name)
        if (value == null) {
            val plainName = name.replaceFirst("^versioning\\.".toRegex(), "")
            val environmentVariableName = plainName
                .split("(?=\\p{Lu})".toRegex())
                .dropLastWhile { it.isEmpty() }
                .joinToString(separator = "_", prefix = "VERSIONING_")
                .replace("\\.".toRegex(), "_")
                .toUpperCase()

            value = System.getenv(environmentVariableName)
        }
        return value
    }

    private fun getUpdateGradlePropertiesOption(gitRefConfig: RefPatchDescription): Boolean {
        val updateGradlePropertiesOption = getCommandOption(OPTION_UPDATE_GRADLE_PROPERTIES)
        if (updateGradlePropertiesOption != null) {
            return java.lang.Boolean.parseBoolean(updateGradlePropertiesOption)
        }
        return if (gitRefConfig.updateGradleProperties != null) {
            gitRefConfig.updateGradleProperties!!
        } else false
    }

    companion object {
        private val VERSION_PATTERN =
            Pattern.compile(".*?(?<version>(?<core>(?<major>\\d+)(?:\\.(?<minor>\\d+)(?:\\.(?<patch>\\d+))?)?)(?:-(?<label>.*))?)|")
        private const val OPTION_NAME_GIT_REF = "git.ref"
        private const val OPTION_NAME_GIT_TAG = "git.tag"
        private const val OPTION_NAME_GIT_BRANCH = "git.branch"
        private const val OPTION_NAME_DISABLE = "versioning.disable"
        private const val OPTION_UPDATE_GRADLE_PROPERTIES = "versioning.updateGradleProperties"

        internal fun matchVersion(input: String): Matcher {
            val matcher = VERSION_PATTERN.matcher(input)
            matcher.find()
            return matcher
        }

        private fun getGitVersionDetails(
            gitSituation: GitSituation,
            config: GitVersioningPluginConfig?
        ): GitVersionDetails? {
            val sortedTags = gitSituation.tags.map { tags ->
                tags.sortedBy { DefaultArtifactVersion(it) }
            }
            for (refConfig in config!!.refs.list) {
                when (refConfig.type) {
                    GitRefType.TAG -> {
                        if (gitSituation.isDetached || config.refs.considerTagsOnBranches) {
                            for (tag in sortedTags.get()) {
                                if (refConfig.pattern == null || refConfig.pattern.matcher(tag).matches()) {
                                    return GitVersionDetails(gitSituation.rev, GitRefType.TAG, tag, refConfig)
                                }
                            }
                        }
                    }

                    GitRefType.BRANCH -> {
                        if (!gitSituation.isDetached) {
                            val branch = gitSituation.getBranch()
                            if (refConfig.pattern == null || refConfig.pattern.matcher(branch).matches()) {
                                return GitVersionDetails(gitSituation.rev, GitRefType.BRANCH, branch!!, refConfig)
                            }
                        }
                    }

                    else -> throw IllegalArgumentException("Unexpected ref type: " + refConfig.type)
                }
            }
            return if (config.rev != null) {
                GitVersionDetails(
                    gitSituation.rev, GitRefType.COMMIT, gitSituation.rev,
                    RefPatchDescription(GitRefType.COMMIT, null, config.rev!!)
                )
            } else null
        }

        private fun generateGitProjectProperties(
            objects: ObjectFactory,
            gitSituation: GitSituation,
            gitVersionDetails: GitVersionDetails
        ): MapProperty<String, String> {
            val properties: MapProperty<String, String> = objects.mapProperty()
            properties.put("git.commit", gitVersionDetails.commit)
            properties.put("git.commit.short", gitVersionDetails.commit.substring(0, 7))
            val headCommitDateTime = gitSituation.getTimestamp()
            properties.put("git.commit.timestamp", headCommitDateTime.toEpochSecond().toString())
            properties.put(
                "git.commit.timestamp.datetime",
                if (headCommitDateTime.toEpochSecond() > 0) {
                    headCommitDateTime.format(DateTimeFormatter.ISO_INSTANT)
                } else {
                    "0000-00-00T00:00:00Z"
                }
            )
            val refName = gitVersionDetails.refName
            val refNameSlug = slugify(refName)
            properties.put("git.ref", refName)
            properties.put("git.ref" + ".slug", refNameSlug)
            return properties
        }

        // ---- misc -------------------------------------------------------------------------------------------------------
        internal fun slugify(value: String): String =
            value.replace("/", "-")

        internal fun increase(number: String, increment: Long): String {
//            val sanitized = number.toLongOrNull() ?: return "0"
            val sanitized = number.ifEmpty { "0" }
            return String.format(
                "%0${sanitized.length}d",
//                sanitized.toLong() + increment
                (number.toLongOrNull() ?: 0L) + increment
            )
        }
    }
}


private fun generateGlobalFormatPlaceholderMap(
    objects: ObjectFactory,
    providers: ProviderFactory,
    gitSituation: GitSituation,
    gitVersionDetails: GitVersionDetails,
    projectProperties: MapProperty<String, *>,
): MapProperty<String, String> {

    val placeholderMap: MapProperty<String, String> = objects.mapProperty<String, String>()
    val hash = providers.provider { gitSituation.rev }
    placeholderMap.put("commit", hash)
    placeholderMap.put("commit.short", hash.map { it.substring(0, 7) })
    val headCommitDateTime = providers.provider { gitSituation.getTimestamp() }
    //@formatter:off
    placeholderMap.put("commit.timestamp", headCommitDateTime.map { it.toEpochSecond().toString() })
    placeholderMap.put("commit.timestamp.year", headCommitDateTime.map { it.year.toString() })
    placeholderMap.put("commit.timestamp.year.2digit", headCommitDateTime.map { (it.year % 100).toString() })
    placeholderMap.put("commit.timestamp.month", headCommitDateTime.map { it.monthValue.toString().padStart(2, '0') })
    placeholderMap.put("commit.timestamp.day", headCommitDateTime.map { it.dayOfMonth.toString().padStart(2, '0') })
    placeholderMap.put("commit.timestamp.hour", headCommitDateTime.map { it.hour.toString().padStart(2, '0') })
    placeholderMap.put("commit.timestamp.minute", headCommitDateTime.map { it.minute.toString().padStart(2, '0') })
    placeholderMap.put("commit.timestamp.second", headCommitDateTime.map { it.second.toString().padStart(2, '0') })
    //@formatter:on
    placeholderMap.put("commit.timestamp.datetime", headCommitDateTime.map {
        if (it.toEpochSecond() > 0) {
            it.format(DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss"))
        } else {
            "00000000.000000"
        }
    })
    val refName = gitVersionDetails.refName
    val refNameSlug = providers.provider { GitVersioningPluginExtension.slugify(refName) }
    placeholderMap.put("ref", providers.provider { refName })
    placeholderMap.put("ref" + ".slug", refNameSlug)
    val refPattern = gitVersionDetails.patchDescription.pattern
    if (refPattern != null) {
        // ref pattern groups
        for ((groupName, value) in patternGroupValues(refPattern, refName)) {
            placeholderMap.put("ref.$groupName", providers.provider { value })
            placeholderMap.put(
                "ref.$groupName.slug",
                providers.provider { GitVersioningPluginExtension.slugify(value) })
        }
    }

    // dirty
    val dirty = providers.provider { !gitSituation.isClean() }
    placeholderMap.put("dirty", dirty.map { if (it) "-DIRTY" else "" })
    placeholderMap.put("dirty.snapshot", dirty.map { if (it) "-SNAPSHOT" else "" })

    // describe
    val description = providers.provider { gitSituation.getDescription() }
    placeholderMap.put("describe", description.map { it.toString() })
    val descriptionTag = description.map { it.tag }
    placeholderMap.put("describe.tag", descriptionTag)
    // describe tag pattern groups
    val describeTagPatternValues = descriptionTag.map { patternGroupValues(gitSituation.describeTagPattern, it) }
    for (groupName in patternGroups(gitSituation.describeTagPattern)) {
        val groupValue = describeTagPatternValues.flatMap { providers.provider { it[groupName] } }
        placeholderMap.put("describe.tag.$groupName", groupValue)
        placeholderMap.put("describe.tag.$groupName.slug", groupValue.map { GitVersioningPluginExtension.slugify(it) })
    }
    val descriptionTagVersionMatcher: Provider<Matcher> = descriptionTag.map { matchVersion(it) }
    placeholderMap.put("describe.tag.version", descriptionTagVersionMatcher.map { it.group("version") ?: "0.0.0" })
    placeholderMap.put("describe.tag.version.core", descriptionTagVersionMatcher.map { it.group("core") ?: "0" })

    val tagMajorVersion = descriptionTagVersionMatcher.map { it.group("major") ?: "0" }
    placeholderMap.put("describe.tag.version.major", tagMajorVersion)
    placeholderMap.put("describe.tag.version.major.next", tagMajorVersion.map { increase(it, 1) })

    val tagMinorVersion = descriptionTagVersionMatcher.map { it.group("minor") ?: "0" }
    placeholderMap.put("describe.tag.version.minor", tagMinorVersion)
    placeholderMap.put("describe.tag.version.minor.next", tagMinorVersion.map { increase(it, 1) })

    val tagPatchVersion = descriptionTagVersionMatcher.map { it.group("patch") ?: "0" }
    placeholderMap.put("describe.tag.version.patch", tagPatchVersion)
    val tagPatchVersionNext = tagPatchVersion.map { increase(it, 1) }
    placeholderMap.put("describe.tag.version.patch.next", tagPatchVersionNext)

    val describeTagVersionLabel = descriptionTagVersionMatcher.map { it.group("label") ?: "" }
//    val describeTagVersionLabelNext = describeTagVersionLabel.map { increase(it, 1) }
    placeholderMap.put("describe.tag.version.label", describeTagVersionLabel)
//    placeholderMap.put("describe.tag.version.label.next", describeTagVersionLabelNext)

    val descriptionDistance = description.map { it.distance.toLong() }
    placeholderMap.put("describe.distance", descriptionDistance.map { it.toString() })
    placeholderMap.put("describe.tag.version.patch.plus.describe.distance",
        providers.zip(descriptionDistance, tagPatchVersion) { dist: Long, patch -> increase(patch, dist) }
    )

    placeholderMap.put("describe.tag.version.patch.next.plus.describe.distance",
        providers.zip(descriptionDistance, tagPatchVersionNext) { dist: Long, patchNext -> increase(patchNext, dist) }
    )

    placeholderMap.put("describe.tag.version.label.plus.describe.distance",
        providers.zip(descriptionDistance, describeTagVersionLabel) { dist: Long, versionLabel ->
            increase(versionLabel, dist)
        }
    )

//    placeholderMap.put("describe.tag.version.label.next.plus.describe.distance",
//        providers.zip(descriptionDistance, describeTagVersionLabelNext) { dist: Long, versionLabelNext ->
//            increase(versionLabelNext, dist)
//        }
//    )
//
    // command parameters e.g. gradle -Pfoo=123 will be available as ${property.foo}
//    placeholderMap.putAll(
//        projectProperties.map { properties ->
//            properties
//                .filterValues { v ->
//                    // filter complex properties
//                    v is String || v is Number
//                }.map { (k, v) ->
//                    "property.$k" to v.toString()
//                }.toMap()
//        }
//    )
//        for ((key, value) in projectProperties) {
//            if (value != null) {
//                // filter complex properties
//                if (value is String || value is Number) {
//                    placeholderMap.put("property.$key", value.toString())
//                }
//            }
//        }

    // environment variables e.g. BUILD_NUMBER=123 will be available as ${env.BUILD_NUMBER}
    System.getenv().forEach { (key, value) ->
        placeholderMap.put("env.$key", value)
    }
    return placeholderMap
}
