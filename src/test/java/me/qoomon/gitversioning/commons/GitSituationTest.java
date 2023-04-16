package me.qoomon.gitversioning.commons;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.gradle.api.model.ObjectFactory;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static me.qoomon.gitversioning.commons.GitUtil.NO_COMMIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.eclipse.jgit.lib.Constants.MASTER;

class GitSituationTest {

    @TempDir
    Path tempDir;

    private final ObjectFactory objects = ProjectBuilder.builder().build().getObjects();

    private GitSituation GitSituation(Repository repository) {
        return GitSituation.newGitSituation(
                objects,
                repository,
                null,
                null,
                null
        );
    }

    @Test
    void revParse_emptyRepo() throws GitAPIException {

        // given
        Git git = Git.init().setInitialBranch("master").setDirectory(tempDir.toFile()).call();

        GitSituation situation = GitSituation(git.getRepository());

        // then
        assertThat(situation).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.isClean()).isTrue();
            softly.assertThat(it.rev).isEqualTo(NO_COMMIT);
            softly.assertThat(it.getBranch()).isEqualTo(MASTER);
            softly.assertThat(it.getTags().getOrNull()).isEmpty();
        }));
    }

    @Test
    void revParse_nonEmptyRepo() throws GitAPIException {

        // given
        Git git = Git.init().setInitialBranch("master").setDirectory(tempDir.toFile()).call();

        RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();

        GitSituation situation = GitSituation(git.getRepository());

        // then
        assertThat(situation).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.isClean()).isTrue();
            softly.assertThat(it.rev).isEqualTo(givenCommit.name());
            softly.assertThat(it.getBranch()).isEqualTo(MASTER);
            softly.assertThat(it.getTags().getOrNull()).isEmpty();
        }));
    }

    @Test
    void headSituation_onBranchWithTag() throws GitAPIException {

        // Given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(tempDir.toFile()).call();
        RevCommit givenCommit = git.commit().setMessage("init").setAllowEmpty(true).call();
        String givenTag = "v1";
        git.tag().setName(givenTag).setObjectId(givenCommit).call();

        GitSituation situation = GitSituation(git.getRepository());

        // Then
        assertThat(situation).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.isClean()).isTrue();
            softly.assertThat(it.rev).isEqualTo(givenCommit.getName());
            softly.assertThat(it.getBranch()).isEqualTo(MASTER);
            softly.assertThat(it.getTags().getOrNull()).containsExactly(givenTag);
        }));
    }

    @Test
    void headSituation_detachedHead() throws GitAPIException {

        // Given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(tempDir.toFile()).call();
        RevCommit givenCommit = git.commit().setMessage("init").setAllowEmpty(true).call();
        git.checkout().setName(givenCommit.getName()).call();

        GitSituation situation = GitSituation(git.getRepository());

        // Then
        assertThat(situation).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.isClean()).isTrue();
            softly.assertThat(it.rev).isEqualTo(givenCommit.getName());
            softly.assertThat(it.getBranch()).isNull();
            softly.assertThat(it.getTags().getOrNull()).isEmpty();
        }));
    }

    @Test
    void headSituation_detachedHeadWithTag() throws GitAPIException {

        // Given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(tempDir.toFile()).call();
        RevCommit givenCommit = git.commit().setMessage("init").setAllowEmpty(true).call();
        String givenTag = "v1";
        git.tag().setName(givenTag).setObjectId(givenCommit).call();
        git.checkout().setName(givenTag).call();

        GitSituation situation = GitSituation(git.getRepository());

        // Then
        assertThat(situation).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.isClean()).isTrue();
            softly.assertThat(it.rev).isEqualTo(givenCommit.getName());
            softly.assertThat(it.getBranch()).isNull();
            softly.assertThat(it.getTags().getOrNull()).containsExactly(givenTag);
        }));
    }

    @Test
    void situation_annotatedTagOnMaster() throws Exception {

        // Given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(tempDir.toFile()).call();
        RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();

        String givenTag = "v1";
        git.tag().setAnnotated(true).setName(givenTag).call();

        GitSituation situation = GitSituation(git.getRepository());

        // Then
        assertThat(situation).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.isClean()).isTrue();
            softly.assertThat(it.rev).isEqualTo(givenCommit.getName());
            softly.assertThat(it.getBranch()).isEqualTo(MASTER);
            softly.assertThat(it.getTags().getOrNull()).containsExactly(givenTag);
        }));
    }

    @Test
    void situation_annotatedTagDetached() throws Exception {

        // Given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(tempDir.toFile()).call();
        RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();

        String givenTag = "v1";
        git.tag().setAnnotated(true).setName(givenTag).setObjectId(givenCommit).call();
        git.checkout().setName(givenTag).call();

        GitSituation situation = GitSituation(git.getRepository());

        // Then
        assertThat(situation).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.isClean()).isTrue();
            softly.assertThat(it.rev).isEqualTo(givenCommit.getName());
            softly.assertThat(it.getBranch()).isNull();
            softly.assertThat(it.getTags().getOrNull()).containsExactly(givenTag);
        }));
    }

    @Test
    void situation_lightweightTagOnMaster() throws Exception {

        // Given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(tempDir.toFile()).call();
        RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();

        String givenTag = "v1";
        git.tag().setAnnotated(false).setName(givenTag).call();

        GitSituation situation = GitSituation(git.getRepository());

        // Then
        assertThat(situation).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.isClean()).isTrue();
            softly.assertThat(it.rev).isEqualTo(givenCommit.getName());
            softly.assertThat(it.getBranch()).isEqualTo(MASTER);
            softly.assertThat(it.getTags().getOrNull()).containsExactly(givenTag);
        }));
    }

    @Test
    void situation_lightweightTagDetached() throws Exception {

        // Given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(tempDir.toFile()).call();
        RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();

        String givenTag = "v1";
        git.tag().setAnnotated(false).setName(givenTag).call();
        git.checkout().setName(givenTag).call();

        GitSituation situation = GitSituation(git.getRepository());

        // Then
        assertThat(situation).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.isClean()).isTrue();
            softly.assertThat(it.rev).isEqualTo(givenCommit.getName());
            softly.assertThat(it.getBranch()).isNull();
            softly.assertThat(it.getTags().getOrNull()).containsExactly(givenTag);
        }));
    }
}
