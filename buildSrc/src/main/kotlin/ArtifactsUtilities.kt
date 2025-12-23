/*
 * Copyright (c) 2024-2025. Müller und Wulff GmbH. All rights reserved.
 */

import org.gradle.api.file.ProjectLayout

object ArtifactsUtilities {
    fun buildName(vararg names: String?) = names.filterNotNull().mapIndexed { index, name ->
        if (index == 0) name else name.capitalize()
    }.joinToString("")

    fun taskNameBundle(buildType: BuildType, flavor: String? = null) =
        buildName("bundle", flavor, buildType.literal)

    fun taskNameAssemble(buildType: BuildType, flavor: String? = null) =
        buildName("assemble", flavor, buildType.literal)

    fun ProjectLayout.aabFolder(buildType: BuildType, flavor: String? = null) =
        "${buildDirectory.get()}/outputs/bundle/" +
                if (flavor == null) buildType.literal else buildName(flavor, buildType.literal)

    fun ProjectLayout.apkFolder(buildType: BuildType = BuildType.DEBUG, flavor: String? = null) =
        "${buildDirectory.get()}/outputs/apk/" +
                if (flavor == null) buildType.literal else "${flavor}/${buildType.literal}"

    fun ProjectLayout.mappingFile(buildType: BuildType, flavor: String? = null): String {
        val folder =
            if (flavor == null) buildType.literal else buildName(flavor, buildType.literal)
        return "${buildDirectory.get()}/outputs/mapping/$folder/mapping.txt"
    }

    private fun String.capitalize() =
        this.replaceFirstChar { if (it.isLowerCase()) it.uppercaseChar() else it }

    enum class BuildType(val literal: String) {
        DEBUG("debug"), RELEASE("release")
    }
}