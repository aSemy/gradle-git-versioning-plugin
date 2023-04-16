package me.qoomon.gitversioning.commons

import org.apache.maven.artifact.versioning.DefaultArtifactVersion
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevTag
import org.eclipse.jgit.revwalk.RevWalk

class TagComparator(repository: Repository?) : Comparator<Ref> {

    private val revWalk: RevWalk = RevWalk(repository)

    override fun compare(ref1: Ref, ref2: Ref): Int {
        val rev1 = revWalk.parseAny(ref1.objectId)
        val rev2 = revWalk.parseAny(ref2.objectId)

        return when {
            // both tags are annotated tags
            rev1 is RevTag && rev2 is RevTag -> compareTaggerDate(rev1, rev2)

            // only ref1 is annotated tag
            rev1 is RevTag -> -1

            // only ref2 is annotated tag
            rev2 is RevTag -> 1

            // both tags are lightweight tags
            else -> compareTagVersion(ref1, ref2)
        }
    }

    companion object {

        private fun compareTagVersion(ref1: Ref, ref2: Ref): Int {
            val version1 = DefaultArtifactVersion(ref1.name)
            val version2 = DefaultArtifactVersion(ref2.name)
            // sort the highest version first
            return -version1.compareTo(version2)
        }

        private fun compareTaggerDate(rev1: RevTag, rev2: RevTag): Int {
            val revTag1Date = rev1.taggerIdent.getWhen()
            val revTag2Date = rev2.taggerIdent.getWhen()
            // sort the most recent tags first
            return -revTag1Date.compareTo(revTag2Date)
        }
    }
}
