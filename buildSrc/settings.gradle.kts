/*
 * Copyright (c) 2026. Müller und Wulff GmbH. All rights reserved.
 */

// Without this, the name is derived from the checkout folder, which changes the generated
// project accessors and so the buildscript classpath - Gradle warns that it breaks caching.
rootProject.name = "buildSrc"

// buildSrc is its own build, so the root build's version catalog isn't visible here
// automatically - point it at the same file so buildSrc's dependencies stay in one place too.
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
