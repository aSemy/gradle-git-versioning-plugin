package me.qoomon.gitversioning;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class GitVersioningTest {

    @Test
    void determineVersion_forBranch() {

        // given
        GitRepoSituation repoSituation = new GitRepoSituation();
        repoSituation.setHeadBranch("develop");

        // when
        GitVersionDetails gitVersionDetails = GitVersioning.determineVersion(repoSituation,
                new VersionDescription(),
                asList(new VersionDescription(null, "${branch}-branch")),
                emptyList(),
                "undefined");

        // then
        assertThat(gitVersionDetails).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.isClean()).isTrue();
            softly.assertThat(it.getCommit()).isEqualTo(repoSituation.getHeadCommit());
            softly.assertThat(it.getCommitRefType()).isEqualTo("branch");
            softly.assertThat(it.getCommitRefName()).isEqualTo(repoSituation.getHeadBranch());
            softly.assertThat(it.getVersion()).isEqualTo(repoSituation.getHeadBranch() + "-branch");
        }));
    }

    @Test
    void determineVersion_forBranchWithTag() {

        // given
        GitRepoSituation repoSituation = new GitRepoSituation();
        repoSituation.setHeadBranch("develop");
        repoSituation.setHeadTags(asList("v1"));


        // when
        GitVersionDetails gitVersionDetails = GitVersioning.determineVersion(repoSituation,
                new VersionDescription(),
                asList(new VersionDescription(null, "${branch}-branch")),
                emptyList(),
                "undefined");

        // then
        assertThat(gitVersionDetails).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.isClean()).isTrue();
            softly.assertThat(it.getCommit()).isEqualTo(repoSituation.getHeadCommit());
            softly.assertThat(it.getCommitRefType()).isEqualTo("branch");
            softly.assertThat(it.getCommitRefName()).isEqualTo(repoSituation.getHeadBranch());
            softly.assertThat(it.getVersion()).isEqualTo(repoSituation.getHeadBranch() + "-branch");
        }));
    }

    @Test
    void determineVersion_detachedHead() {

        // given
        GitRepoSituation repoSituation = new GitRepoSituation();


        // when
        GitVersionDetails gitVersionDetails = GitVersioning.determineVersion(repoSituation,
                new VersionDescription(null, "${commit}-commit"),
                emptyList(),
                emptyList(),
                "undefined");

        // then
        assertThat(gitVersionDetails).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.isClean()).isTrue();
            softly.assertThat(it.getCommit()).isEqualTo(repoSituation.getHeadCommit());
            softly.assertThat(it.getCommitRefType()).isEqualTo("commit");
            softly.assertThat(it.getCommitRefName()).isEqualTo(repoSituation.getHeadCommit());
            softly.assertThat(it.getVersion()).isEqualTo(repoSituation.getHeadCommit() + "-commit");
        }));
    }

    @Test
    void determineVersion_detachedHeadWithTag() {

        // given
        GitRepoSituation repoSituation = new GitRepoSituation();
        repoSituation.setHeadTags(asList("v1"));

        // when
        GitVersionDetails gitVersionDetails = GitVersioning.determineVersion(repoSituation,
                new VersionDescription(),
                emptyList(),
                asList(new VersionDescription("v.*", "${tag}-tag")),
                "undefined");


        // then
        assertThat(gitVersionDetails).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.isClean()).isTrue();
            softly.assertThat(it.getCommit()).isEqualTo(repoSituation.getHeadCommit());
            softly.assertThat(it.getCommitRefType()).isEqualTo("tag");
            softly.assertThat(it.getCommitRefName()).isEqualTo(repoSituation.getHeadTags().get(0));
            softly.assertThat(it.getVersion()).isEqualTo(repoSituation.getHeadTags().get(0) + "-tag");
        }));
    }

    @Test
    void determineVersion_forBranchWithTimestamp() {

        // given
        GitRepoSituation repoSituation = new GitRepoSituation();
        repoSituation.setHeadBranch("develop");
        Instant instant = ZonedDateTime.of(2019, 4, 23, 10, 12, 45, 0, ZoneOffset.UTC).toInstant();
        repoSituation.setHeadCommitTimestamp(instant.getEpochSecond());

        // when
        GitVersionDetails gitVersionDetails = GitVersioning.determineVersion(repoSituation,
                new VersionDescription(),
                asList(new VersionDescription(null, "${commit.timestamp}-branch")),
                emptyList(),
                "undefined");

        // then
        assertThat(gitVersionDetails).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.isClean()).isTrue();
            softly.assertThat(it.getCommit()).isEqualTo(repoSituation.getHeadCommit());
            softly.assertThat(it.getCommitRefType()).isEqualTo("branch");
            softly.assertThat(it.getCommitRefName()).isEqualTo(repoSituation.getHeadBranch());
            softly.assertThat(it.getVersion()).isEqualTo(instant.getEpochSecond() + "-branch");
        }));
    }

    @Test
    void determineVersion_forBranchWithDateTime() {

        // given
        GitRepoSituation repoSituation = new GitRepoSituation();
        repoSituation.setHeadBranch("develop");
        Instant instant = ZonedDateTime.of(2019, 4, 23, 10, 12, 45, 0, ZoneOffset.UTC).toInstant();
        repoSituation.setHeadCommitTimestamp(instant.getEpochSecond());

        // when
        GitVersionDetails gitVersionDetails = GitVersioning.determineVersion(repoSituation,
                new VersionDescription(),
                asList(new VersionDescription(null, "${commit.timestamp.datetime}-branch")),
                emptyList(),
                "undefined");

        // then
        assertThat(gitVersionDetails).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.isClean()).isTrue();
            softly.assertThat(it.getCommit()).isEqualTo(repoSituation.getHeadCommit());
            softly.assertThat(it.getCommitRefType()).isEqualTo("branch");
            softly.assertThat(it.getCommitRefName()).isEqualTo(repoSituation.getHeadBranch());
            softly.assertThat(it.getVersion()).isEqualTo(
                    DateTimeFormatter.ofPattern(GitVersioning.VERSION_DATE_TIME_FORMAT).withZone(ZoneOffset.UTC).format(instant) + "-branch");
        }));
    }
}