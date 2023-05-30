import java.net.URI
import java.time.Instant
import java.util.Properties


plugins {
    kotlin("jvm") version "1.8.21"
    java
    `maven-publish`
    jacoco
    signing
    id("com.github.johnrengelman.shadow") version "8.1.1"

    id("org.cyclonedx.bom") version "1.7.2"
    id("com.github.spotbugs") version "5.0.13"
    // TODO: more static analysis. E.g.:
    // id("com.diffplug.spotless") version "6.11.0"
}

repositories {
    mavenCentral()
    google()
}

val r8 = configurations.create("r8")

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    testCompileOnly("junit:junit:4.13")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
    testImplementation("org.hamcrest:hamcrest:2.2")
    testImplementation("pl.pragmatists:JUnitParams:1.1.1")
    testImplementation("com.google.code.tempus-fugit:tempus-fugit:1.1")

    r8("com.android.tools:r8:8.0.40")
}

group = "com.amazon.ion"
// The version is stored in a separate file so that we can easily create CI workflows that run when the version changes
// and so that any tool can access the version without having to do any special parsing.
version = File(project.rootDir.path + "/project.version").readLines().single()
description = "A Java implementation of the Amazon Ion data notation."
java.sourceCompatibility = JavaVersion.VERSION_1_8
java.targetCompatibility = JavaVersion.VERSION_1_8

val isReleaseVersion: Boolean = !version.toString().endsWith("SNAPSHOT")
val generatedJarInfoDir = "${buildDir}/generated/jar-info"
lateinit var sourcesArtifact: PublishArtifact
lateinit var javadocArtifact: PublishArtifact

sourceSets {
    main {
        java.srcDir("src")
        resources.srcDir(generatedJarInfoDir)
    }
    test {
        java.srcDir("test")
    }
}

kotlin {
    jvmToolchain(8)
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        // In Java 9+ we can use `release` but for now we're still building with JDK 8, 11
    }

    jar {
        archiveClassifier.set("original")
    }

    shadowJar {
        relocate("kotlin", "com.amazon.ion_.shaded.kotlin")
        minimize()
    }

    val minifiedJar by register<JavaExec>("r8Jar") {
        val rules = file("config/r8/rules.txt")

        inputs.file("build/libs/ion-java-$version-all.jar")
        inputs.file(rules)
        outputs.file("build/libs/ion-java-$version.jar")

        dependsOn(shadowJar)
        dependsOn(configurations.runtimeClasspath)

        classpath(r8)
        mainClass.set("com.android.tools.r8.R8")
        args = listOf(
            "--release",
            "--classfile",
            "--output", "build/libs/ion-java-$version.jar",
            "--pg-conf", rules.toString(),
            "--lib", System.getProperty("java.home").toString(),
            "build/libs/ion-java-$version-all.jar",
        )
    }

    build {
        dependsOn(minifiedJar)
    }

    javadoc {
        // Suppressing Javadoc warnings is clunky, but there doesn't seem to be any nicer way to do it.
        // https://stackoverflow.com/questions/62894307/option-xdoclintnone-does-not-work-in-gradle-to-ignore-javadoc-warnings
        options {
            this as StandardJavadocDocletOptions
            addBooleanOption("Xdoclint:none", true)
            addStringOption("Xmaxwarns", "1") // best we can do is limit warnings to 1
        }
    }

    // spotbugs-gradle-plugin creates a :spotbugsTest task by default, but we don't want it
    // see: https://github.com/spotbugs/spotbugs-gradle-plugin/issues/391
    project.gradle.startParameter.excludedTaskNames.add(":spotbugsTest")

    spotbugsMain {
        val spotbugsBaselineFile = "$rootDir/config/spotbugs/baseline.xml"

        // CI=true means we're in a CI workflow
        // https://docs.github.com/en/actions/learn-github-actions/variables#default-environment-variables
        val ciWorkflow = System.getenv()["CI"].toBoolean()
        val baselining = project.hasProperty("baseline") // e.g. `./gradlew :spotbugsMain -Pbaseline`

        if (!baselining) {
            baselineFile.set(file(spotbugsBaselineFile))
        }

        // The plugin only prints to console when no reports are configured
        // See: https://github.com/spotbugs/spotbugs-gradle-plugin/issues/172
        if (!ciWorkflow && !baselining) {
            reports.create("html").required.set(true)
        } else if (baselining) {
            // Note this path succeeds :spotbugsMain because *of course it does*
            ignoreFailures = true
            reports.create("xml") {
                // Why bother? Otherwise we have kilobytes of workspace-specific absolute paths, statistics, etc.
                // cluttering up the baseline XML and preserved in perpetuity. It would be far better if we could use
                // the SpotBugs relative path support, but the way SpotBugs reporters are presently architected they
                // drop the `destination` information long before Project.writeXML uses its presence/absence to
                // determine whether to generate relative instead of absolute paths. So, contribute patch to SpotBugs or
                // write own SpotBugs reporter in parallel or... do this.
                // Improvements are definitely possible, but left as an exercise to the reader.
                doLast {
                    // It would be super neat if we had some way to handle this xml processing directly inline, without
                    // generating a temp file or at least without shelling out.
                    // Use ant's xslt capabilities? Xalan? Saxon gradle plugin (eerohele)? javax.xml.transform?
                    // In the mean time... xsltproc!
                    exec {
                        commandLine(
                            "xsltproc",
                            "--output", spotbugsBaselineFile,
                            "$rootDir/config/spotbugs/baseline.xslt",
                            outputLocation.get().toString()
                        )
                    }
                }
            }
        }
    }

    create<Jar>("sourcesJar") sourcesJar@{
        archiveClassifier.set("sources")
        from(sourceSets["main"].java.srcDirs)
        artifacts { sourcesArtifact = archives(this@sourcesJar) }
    }

    create<Jar>("javadocJar") javadocJar@{
        archiveClassifier.set("javadoc")
        from(javadoc)
        artifacts { javadocArtifact = archives(this@javadocJar) }
    }

    /**
     * This task creates a properties file that will be included in the compiled jar. It contains information about
     * the build/version of the library used in [com.amazon.ion.util.JarInfo]. See https://github.com/amazon-ion/ion-java/pull/433
     * for why this is done with a properties file rather than the Jar manifest.
     */
    val generateJarInfo by creating<Task> {
        doLast {
            val propertiesFile = File("$generatedJarInfoDir/${project.name}.properties")
            propertiesFile.parentFile.mkdirs()
            val properties = Properties()
            properties.setProperty("build.version", version.toString())
            properties.setProperty("build.time", Instant.now().toString())
            properties.store(propertiesFile.writer(), null)
        }
        outputs.dir(generatedJarInfoDir)
    }

    processResources { dependsOn(generateJarInfo) }

    jacocoTestReport {
        dependsOn(test)
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
        doLast {
            logger.quiet("Coverage report written to file://${reports.html.outputLocation.get()}/index.html")
        }
    }

    test {
        maxHeapSize = "1g" // When this line was added Xmx 512m was the default, and we saw OOMs
        maxParallelForks = Math.max(1, Runtime.getRuntime().availableProcessors() / 2)
        useJUnitPlatform()
        // report is always generated after tests run
        finalizedBy(jacocoTestReport)
    }

    val testMinified by register<Test>("testMinified") {
        maxHeapSize = "1g" // When this line was added Xmx 512m was the default, and we saw OOMs
        maxParallelForks = Math.max(1, Runtime.getRuntime().availableProcessors() / 2)
        group = "verification"
        testClassesDirs = project.sourceSets.test.get().output.classesDirs
        classpath = project.sourceSets.test.get().runtimeClasspath + minifiedJar.outputs.files
        dependsOn(minifiedJar)
        useJUnitPlatform()
    }

    withType<Sign> {
        setOnlyIf { isReleaseVersion && gradle.taskGraph.hasTask(":publish") }
    }

    cyclonedxBom {
        setIncludeConfigs(listOf("runtimeClasspath"))
        setSkipConfigs(listOf("compileClasspath", "testCompileClasspath"))
    }
}

publishing {
    publications.create<MavenPublication>("IonJava") {
        from(components["java"])
        artifact(sourcesArtifact)
        artifact(javadocArtifact)

        pom {
            name.set("Ion Java")
            description.set(project.description)
            url.set("https://github.com/amazon-ion/ion-java/")

            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    name.set("Amazon Ion Team")
                    email.set("ion-team@amazon.com")
                    organization.set("Amazon Ion")
                    organizationUrl.set("https://github.com/amazon-ion")
                }
            }
            scm {
                connection.set("scm:git:git@github.com:amazon-ion/ion-java.git")
                developerConnection.set("scm:git:git@github.com:amazon-ion/ion-java.git")
                url.set("git@github.com:amazon-ion/ion-java.git")
            }
        }
    }
    repositories.mavenCentral {
        credentials {
            username = properties["ossrhUsername"].toString()
            password = properties["ossrhPassword"].toString()
        }
        url = URI.create("https://aws.oss.sonatype.org/service/local/staging/deploy/maven2")
    }
}

signing {
    // Allow publishing to maven local even if we don't have the signing keys
    // This works because when not required, the signing task will be skipped
    // if signing.keyId, signing.password, signing.secretKeyRingFile, etc are
    // not present in gradle.properties.
    isRequired = isReleaseVersion
    sign(publishing.publications["IonJava"])
}
