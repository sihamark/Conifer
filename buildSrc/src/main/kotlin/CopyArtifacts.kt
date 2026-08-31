/*
 * Copyright (c) 2024-2025. Müller und Wulff GmbH. All rights reserved.
 */

import ArtifactsUtilities.BuildType
import ArtifactsUtilities.BuildType.DEBUG
import ArtifactsUtilities.BuildType.RELEASE
import ArtifactsUtilities.aabFolder
import ArtifactsUtilities.apkFolder
import ArtifactsUtilities.mappingFile
import ArtifactsUtilities.taskNameAssemble
import ArtifactsUtilities.taskNameBundle
import CopyArtifacts.FileType.AAB
import CopyArtifacts.FileType.APK
import CopyArtifacts.FileType.MAPPING
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

/*
 * Copyright (c) 2024. Müller & Wulff. All rights reserved.
 */

/**
 * A gradle task that copies the artifacts from the build directory to the release folder.
 *
 * inputs are:
 * - ``appName``: the name of the app, is used when renaming files
 * - ``flavorName``: the name of the flavor, is used to determine the folder to copy from, can be null
 * - ``buildType``: the build type, is used to determine the folder to copy from (either DEBUG or RELEASE)
 * - ``intoFolder``: the folder to copy the artifacts to
 *
 * when ``DEBUG`` is selected only the APK is build, not the AAB.
 *
 * @see ArtifactsUtilities
 * @see AppConfig
 */
abstract class CopyArtifacts @Inject constructor(
    @Inject private val files: FileSystemOperations,
    @Inject private val layout: ProjectLayout,
) : DefaultTask() {

    @get:Input
    abstract val appName: Property<String>

    @get:Input
    abstract val flavorName: Property<String>

    @get:Input
    abstract val buildType: Property<BuildType>

    @get:Input
    abstract val versionProperties: Property<VersionProperties>

    @get:Input
    abstract val intoFolder: Property<String>

    /**
     * Sets up the task with the given parameters.
     * It also sets the task group to ``release`` and the description to a meaningful message.
     *
     * It sets the task dependencies base on the [buildType] and [flavorName].
     * When the [buildType] is ``DEBUG`` it depends on the assemble task, so it builds an APK.
     * When the [buildType] is ``RELEASE`` it depends on the assemble and the bundle task, so it builds an APK and an AAB.
     *
     * @param appName the name of the app, is used when renaming files
     * @param buildType the build type, is used to determine the folder to copy from (either DEBUG or RELEASE)
     * @param versionProperties the version properties of the app
     * @param intoFolder the folder to copy the artifacts to
     * @param flavorName the name of the flavor, is used to determine the folder to copy from, can be null
     */
    fun setup(
        appName: String,
        buildType: BuildType,
        versionProperties: VersionProperties,
        intoFolder: String,
        flavorName: String? = null,
    ) {
        group = "release"
        val flavorDescription = if (flavorName == null) "" else " for flavor $flavorName"
        val buildTypeDescription = if (buildType == DEBUG) "debug" else "release"
        description =
            "Copies the artifacts$flavorDescription ($buildTypeDescription) from the build directory to the release folder ($intoFolder)."

        this.appName.set(appName)
        this.flavorName.set(flavorName ?: "")
        this.buildType.set(buildType)
        this.versionProperties.set(versionProperties)
        this.intoFolder.set(intoFolder)

        dependsOn(taskNameAssemble(buildType, flavorName))
        if (buildType == RELEASE) {
            dependsOn(taskNameBundle(buildType, flavorName))
        }
    }

    private fun appName(type: FileType): String {
        val appName = appName.get()
        val flavorDot = flavorName.get().takeIf { it.isNotBlank() }?.let { "$it." } ?: ""
        val versionProperties = versionProperties.get()
        val versionName = versionProperties.versionName
        val versionCode = versionProperties.versionCode
        val versionSuffix = if (buildType.get() == DEBUG) "-D" else ""
        return "$appName.$flavorDot$versionName$versionSuffix.$versionCode.${type.ending}"
    }

    @TaskAction
    fun action() {
        files.copy {
            // An absent flavor is held as "" - a Property<String> cannot hold null - but the folder
            // helpers below test for null, so "" takes the flavored branch and builds
            // `bundle/Release` and `mapping/Release` instead of the lowercase folders AGP writes.
            // A case-insensitive filesystem finds them anyway; a Linux CI runner does not, and a
            // copy from a folder that is not there says nothing - so the AAB and the mapping were
            // quietly left behind on Linux while the APK, whose path merely doubles a slash, came
            // through.
            val flavor = flavorName.get().takeIf { it.isNotBlank() }
            val buildType = buildType.get()
            // copy the APK
            from(layout.apkFolder(buildType, flavor)) {
                include("*.apk")
                rename { appName(APK) }
            }
            if (buildType == RELEASE) {
                // copy the AAB and the mapping file only for the release build
                from(layout.aabFolder(buildType, flavor)) {
                    include("*.aab")
                    rename { appName(AAB) }
                }
                from(layout.mappingFile(buildType, flavor)) {
                    rename { appName(MAPPING) }
                }
            }
            into(intoFolder.get())
        }
    }

    class VersionProperties(
        val versionName: String,
        val versionCode: Int
    )

    private enum class FileType(val ending: String) {
        APK("apk"), AAB("aab"), MAPPING("mapping.txt")
    }
}