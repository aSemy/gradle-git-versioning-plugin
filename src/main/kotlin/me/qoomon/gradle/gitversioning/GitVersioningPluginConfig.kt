package me.qoomon.gradle.gitversioning

import me.qoomon.gitversioning.commons.GitRefType
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import java.util.regex.Pattern
import javax.inject.Inject

abstract class GitVersioningPluginConfig @Inject constructor(
    private val objectFactory: ObjectFactory,
) {
    @JvmField
    var disable = false
    var projectVersionPattern: String? = null
    fun projectVersionPattern(): Pattern? = projectVersionPattern?.let { Pattern.compile(it) }


    @JvmField
    var describeTagPattern: String? = null

    @JvmField
    var updateGradleProperties: Boolean? = null

    @JvmField
    val refs = objectFactory.newInstance(RefPatchDescriptionList::class.java)

    @JvmField
    var rev: PatchDescription? = null
    fun refs(action: Action<RefPatchDescriptionList?>) {
        action.execute(refs)
    }

    fun rev(action: Action<PatchDescription?>) {
        rev = objectFactory.newInstance(PatchDescription::class.java)
        action.execute(rev!!)
    }

    open class PatchDescription {
        @JvmField
        var describeTagPattern: String? = null

        fun getDescribeTagPattern(): Pattern? = describeTagPattern?.let { Pattern.compile(it) }

        @JvmField
        var updateGradleProperties: Boolean? = null

        @JvmField
        var version: String? = null

        // WORKAROUND Groovy MetaClass properties API field name conflict
        @JvmField
        var properties_: Map<String, String> = HashMap()
    }

    class RefPatchDescription(
        @JvmField val type: GitRefType,
        @JvmField val pattern: Pattern?
    ) : PatchDescription() {
        constructor(
            type: GitRefType,
            pattern: Pattern?,
            patch: PatchDescription
        ) : this(type, pattern) {
            describeTagPattern = patch.describeTagPattern
            updateGradleProperties = patch.updateGradleProperties
            version = patch.version
            properties_ = patch.properties_
        }
    }

    abstract class RefPatchDescriptionList {
        @JvmField
        var considerTagsOnBranches = false

        @JvmField
        var list: MutableList<RefPatchDescription> = ArrayList()
        fun branch(pattern: String, action: Action<RefPatchDescription?>) {
            val ref = RefPatchDescription(GitRefType.BRANCH, Pattern.compile(pattern))
            action.execute(ref)
            list.add(ref)
        }

        fun tag(pattern: String, action: Action<RefPatchDescription?>) {
            val ref = RefPatchDescription(GitRefType.TAG, Pattern.compile(pattern))
            action.execute(ref)
            list.add(ref)
        }
    }

    companion object {
        private val MATCH_ALL = Pattern.compile(".*")
    }
}
