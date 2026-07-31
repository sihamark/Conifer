/*
 * Copyright (c) 2023-2025. Müller und Wulff GmbH. All rights reserved.
 */

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    // For org.apache.tools.zip, the only zip writer that can record Unix permissions.
    // java.util.zip cannot, and Gradle's own api jar doesn't expose Ant's.
    implementation(libs.ant)
}