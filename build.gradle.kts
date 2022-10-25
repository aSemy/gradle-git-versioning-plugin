plugins {
    id "com.github.ben-manes.versions" version "0.43.0"

    id "java"
    id "java-gradle-plugin"
    id 'com.adarshr.test-logger' version '3.2.0'

    id "com.gradle.plugin-publish" version "1.0.0"
    id "maven-publish" // for local testing only

    id "idea"
}

group 'me.qoomon'
version '6.3.5'
sourceCompatibility = JavaVersion.VERSION_11
targetCompatibility = JavaVersion.VERSION_11

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.eclipse.jgit:org.eclipse.jgit:6.3.0.202209071007-r'
    implementation 'org.apache.maven:maven-artifact:3.8.6'
    implementation 'org.apache.commons:commons-configuration2:2.8.0'

    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.9.1'
    testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.9.1'
    testImplementation 'org.assertj:assertj-core:3.23.1'
}

test {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        gitVersioning {
            id = 'me.qoomon.git-versioning'
            displayName= 'Git Versioning Plugin'
            description = 'This extension will adjust the project version, based on current git branch or tag.'
            implementationClass = 'me.qoomon.gradle.gitversioning.GitVersioningPlugin'
        }
    }
}


pluginBundle {
    website = 'https://github.com/qoomon/gradle-git-versioning-plugin'
    vcsUrl = 'https://github.com/qoomon/gradle-git-versioning-plugin.git'
    tags = ['git', 'versioning', 'version', 'commit', 'branch', 'tag', 'generated']
}
