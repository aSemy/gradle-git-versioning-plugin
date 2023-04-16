package me.qoomon.gitversioning.commons

import java.util.regex.Pattern

object StringUtil {
    private val placeholderPattern = Pattern.compile("""\$\{(?<key>[^}:]+)(?::(?<modifier>[-+])(?<value>[^}]*))?}""")

    private val groupNamePattern = Pattern.compile("""\(\?<(?<name>[a-zA-Z][a-zA-Z0-9]*)>""")

    @JvmStatic
    fun substituteText(
        text: String,
        replacements: Map<String, String>
    ): String {
        val result = StringBuffer()
        val placeholderMatcher = placeholderPattern.matcher(text)
        while (placeholderMatcher.find()) {
            val placeholderKey = placeholderMatcher.group("key")
            var replacement = replacements[placeholderKey]
            val placeholderModifier = placeholderMatcher.group("modifier")
            if (placeholderModifier != null) {
                if (placeholderModifier == "-" && replacement == null) {
                    replacement = placeholderMatcher.group("value")
                }
                if (placeholderModifier == "+" && replacement != null) {
                    replacement = placeholderMatcher.group("value")
                }
            }
            if (replacement != null) {
                // avoid group name replacement behaviour of replacement parameter value
                placeholderMatcher.appendReplacement(result, "")
                result.append(replacement)
            }
        }
        placeholderMatcher.appendTail(result)
        return result.toString()
    }

    /**
     * @param pattern pattern
     * @param text  to parse
     * @return a map of group-index and group-name to matching value
     */
    @JvmStatic
    fun patternGroupValues(pattern: Pattern, text: String): Map<String, String> {
        val result: MutableMap<String, String> = HashMap()
        val groupMatcher = pattern.matcher(text)
        if (groupMatcher.find()) {
            // group index values
            for (i in 1..groupMatcher.groupCount()) {
                result[i.toString()] = groupMatcher.group(i)
            }
            // group name values
            for (groupName in patternGroupNames(pattern)) {
                result[groupName] = groupMatcher.group(groupName)
            }
        }
        return result
    }

    @JvmStatic
    fun patternGroups(pattern: Pattern): Set<String> {
        val groups: MutableSet<String> = HashSet()

        // group indexes
        for (groupIndex in 1..patternGroupCount(pattern)) {
            groups.add(groupIndex.toString())
        }
        // group names
        groups.addAll(patternGroupNames(pattern))
        return groups
    }

    fun patternGroupCount(pattern: Pattern): Int {
        return pattern.matcher("").groupCount()
    }

    fun patternGroupNames(pattern: Pattern): Set<String> {
        val groups: MutableSet<String> = HashSet()

        // group names
        val groupNameMatcher = groupNamePattern.matcher(pattern.toString())
        while (groupNameMatcher.find()) {
            val groupName = groupNameMatcher.group("name")
            groups.add(groupName)
        }
        return groups
    }
}
