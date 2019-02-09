package me.qoomon.gitversioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class StringUtilTest {

    @Test
    void substituteText() {
        // Given
        String givenText = "${type}tale";
        Map<String, String> givemSubstitutionMap = new HashMap<>();
        givemSubstitutionMap.put("type", "fairy");

        // When
        String outputText = StringUtil.substituteText(givenText, givemSubstitutionMap);

        // Then
        assertThat(outputText).isEqualTo("fairytale");
    }

    @Test
    void valueGroupMap() {

        // Given
        String givenRegex = "(one) (two) (three)";
        String givenText = "one two three";

        // When
        Map<String, String> valueMap = StringUtil.valueGroupMap(givenRegex, givenText);

        // Then
        assertThat(valueMap).contains(entry("0", givenText), entry("1", "one"), entry("2", "two"), entry("3", "three"));
    }

    @Test
    void valueGroupMap_nested() {

        // Given
        String givenRegex = "(one) (two (three))";
        String givenText = "one two three";

        // When
        Map<String, String> valueMap = StringUtil.valueGroupMap(givenRegex, givenText);

        // Then
        assertThat(valueMap).contains(entry("0", givenText), entry("1", "one"), entry("2", "two three"), entry("3", "three"));
    }

    @Test
    void getRegexGroupValueMap_namedGroup() {

        // Given
        String givenRegex = "(?<first>one) (?<second>two) (?<third>three)";
        String givenText = "one two three";

        // When
        Map<String, String> valueMap = StringUtil.valueGroupMap(givenRegex, givenText);

        // Then
        assertThat(valueMap).contains(entry("0", givenText), entry("1", "one"), entry("2", "two"), entry("3", "three"));
        assertThat(valueMap).contains(entry("first", "one"), entry("second", "two"), entry("third", "three"));
    }

    @Test
    void getRegexGroupValueMap_namedGroupNested() {

        // Given
        String givenRegex = "(?<first>one) (?<second>two (?<third>three))";
        String givenText = "one two three";

        // When
        Map<String, String> valueMap = StringUtil.valueGroupMap(givenRegex, givenText);

        // Then
        assertThat(valueMap).contains(entry("0", givenText), entry("1", "one"), entry("2", "two three"), entry("3", "three"));
        assertThat(valueMap).contains(entry("first", "one"), entry("second", "two three"), entry("third", "three"));
    }
}