// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
import org.jetbrains.intellij.platform.gradle.tasks.aware.CoroutinesJavaAgentAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.SandboxAware
import software.aws.toolkits.gradle.ciOnly
import software.aws.toolkits.gradle.intellij.IdeFlavor
import software.aws.toolkits.gradle.intellij.ToolkitIntelliJExtension

val intellijToolkit = project.extensions.create("intellijToolkit", ToolkitIntelliJExtension::class)
// TODO: how did this break?
when {
    project.name.contains("jetbrains-rider") -> {
        intellijToolkit.ideFlavor.set(IdeFlavor.RD)
    }

    project.name.contains("jetbrains-ultimate") -> {
        intellijToolkit.ideFlavor.set(IdeFlavor.IU)
    }

    project.name.contains("jetbrains-gateway") -> {
        intellijToolkit.ideFlavor.set(IdeFlavor.GW)
    }

    else -> {
        intellijToolkit.ideFlavor.set(IdeFlavor.IC)
    }
}

plugins {
    id("org.jetbrains.intellij.platform.module")
}

intellijPlatform {
    instrumentCode = false
}

// Don't attach the kotlinx-coroutines debug javaagent to headless IDE launches (the per-module `test` task, plus
// runIde / buildSearchableOptions). The agent we resolve is pinned to our coroutines version (kotlinCoroutines in
// libs.versions.toml), but the 2026.1 platform ships coroutines that emit debug-metadata v2; the older agent rejects
// it at runtime ("Debug metadata version mismatch. Expected: 1, got 2"), which crashes IDE startup (plugin descriptor
// loading + Fleet kernel) and hangs the launching task until the CI timeout. The agent only adds coroutine debug
// stacktraces, which these headless launches don't need. The IntelliJ plugin appends the -javaagent argument only when
// this file property is present, so clearing it drops the argument entirely. Applied here (the common base convention)
// so it covers both the publishable root plugins and the leaf test modules, whose `test` task is CoroutinesJavaAgentAware.
tasks.withType<Task>().configureEach {
    if (this is CoroutinesJavaAgentAware) {
        coroutinesJavaAgentFile.set(provider { null })
    }
}

// CI keeps running out of RAM, so limit IDE instance count to 4
ciOnly {
    abstract class NoopBuildService : BuildService<BuildServiceParameters.None> {}
    val noopService = gradle.sharedServices.registerIfAbsent("noopService", NoopBuildService::class.java) {
        maxParallelUsages = 2
    }

    tasks.matching { it is Test || it is SandboxAware }.configureEach {
        usesService(noopService)
    }
}
