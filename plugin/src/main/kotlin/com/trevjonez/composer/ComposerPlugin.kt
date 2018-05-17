/*
 *    Copyright 2017 Trevor Jones
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.trevjonez.composer

import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.ApplicationVariant
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import java.io.File
import kotlin.reflect.KClass

class ComposerPlugin : Plugin<Project> {

    companion object {
        const val GROUP = "Test Automation"
    }

    lateinit var config: ConfigExtension

    override fun apply(project: Project) {
        ComposerTask.createComposerConfiguration(project)
        config = project.extensions.create("composer", ConfigExtension::class.java, project)
        project.afterEvaluate(this::observeVariants)
    }

    private fun observeVariants(project: Project) {
        val androidExtension = project.extensions.getByName("android")

        @Suppress("FoldInitializerAndIfToElvis")
        if (androidExtension == null)
            throw NullPointerException("Android configuration not found on the current project. \n" +
                    "composer is only applicable to android application builds")

        if (androidExtension !is AppExtension) {
            throw IllegalStateException("composer plugin only applicable to android application projects, open an issue if you need something else.")
        }

        androidExtension.applicationVariants.all {
            if (config.variants.isEmpty() || config.variants.contains(it.name)) {
                if (it.testVariant == null) return@all

                val assembleTask: Task = project.tasks.findByName("assemble${it.name.capitalize()}")
                        ?: throw IllegalStateException("Couldn't find assemble task for variant ${it.name}")

                val assembleTestTask: Task = project.tasks.findByName("assemble${it.name.capitalize()}AndroidTest")
                        ?: throw IllegalStateException("Couldn't find assemble android test task for variant ${it.name}")

                val configurator: ConfiguratorDomainObj? = config.configs.findByName(it.name)
                project.createTask(
                        type = ComposerTask::class,
                        name = "run${it.name.capitalize()}AutomationOnConnectedDevices",
                        description = "Run automated tests for ${it.name} on connected devices",
                        dependsOn = listOf(assembleTask, assembleTestTask))
                        .apply {
                            environment("ANDROID_HOME", androidExtension.sdkDirectory.absolutePath)
                            instrumentationArguments.addAll(collectInstrumentationArgs(configurator))
                            apk = getApk(it, configurator)
                            testApk = getTestApk(it, configurator)
                            shard = configurator?.shard
                            outputDirectory = getOutputDirectory(it, configurator, project)
                            verboseOutput = configurator?.verboseOutput
                            devices = configurator?.devices ?: mutableListOf()
                            devicePattern = configurator?.devicePattern
                            keepOutput = configurator?.keepOutput
                            apkInstallTimeout = configurator?.apkInstallTimeout
                            configurator?.configureTask?.execute(this)
                        }

                project.createTask(
                        type = ComposerTask::class,
                        name = "run${it.name.capitalize()}AutomationOnEc2",
                        description = "Run automated tests for ${it.name} on devices specified in emulators.txt",
                        dependsOn = listOf(assembleTask, assembleTestTask))
                        .apply {
                            environment("ANDROID_HOME", androidExtension.sdkDirectory.absolutePath)
                            instrumentationArguments.addAll(collectInstrumentationArgs(configurator))
                            apk = getApk(it, configurator)
                            testApk = getTestApk(it, configurator)
                            shard = configurator?.shard
                            outputDirectory = getOutputDirectory(it, configurator, project)
                            verboseOutput = configurator?.verboseOutput
                            remoteHostsFile = getRemoteHostFile(configurator, project)
                            keepOutput = configurator?.keepOutput
                            apkInstallTimeout = configurator?.apkInstallTimeout
                            configurator?.configureTask?.execute(this)
                        }
            }
        }
    }

    private fun <T : DefaultTask> Project.createTask(type: KClass<T>,
                                                     name: String,
                                                     group: String = GROUP,
                                                     description: String? = null,
                                                     dependsOn: List<Task>? = null)
            : T {
        return type.java.cast(project.tasks.create(LinkedHashMap<String, Any>().apply {
            put("name", name)
            put("type", type.java)
            put("group", group)
            description?.let { put("description", it) }
            dependsOn?.let { put("dependsOn", it) }
        }))
    }

    private fun collectInstrumentationArgs(configurator: ComposerConfigurator?): List<Pair<String, String>> {
        return mutableListOf<Pair<String, String>>().apply {
            addAll(config.instrumentationArguments)
            configurator?.let { addAll(it.instrumentationArguments) }
        }
    }

    private fun getRemoteHostFile(configurator: ComposerConfigurator?, project: Project) : String {
        return configurator?.remoteHostsFile ?: project.rootDir.absolutePath + "/emulators.txt"
    }

    private fun getApk(variant: ApplicationVariant,
                       configurator: ComposerConfigurator?): File {
        try {
            return configurator?.apk ?: apkForVariant(variant)
        } catch (multipleFiles: IllegalArgumentException) {
            throw IllegalStateException("Multiple APK outputs found, " +
                    "You must define the apk to use for composer task on variant ${variant.name}", multipleFiles)
        } catch (noFiles: NoSuchElementException) {
            throw IllegalStateException("No APK output found," +
                    "You must define the testApk to use for composer task on variant ${variant.name}", noFiles)
        }

    }

    private fun getTestApk(variant: ApplicationVariant,
                           configurator: ComposerConfigurator?): File {
        try {
            return configurator?.testApk ?: testApkForVariant(variant)
        } catch (multipleFiles: IllegalArgumentException) {
            throw IllegalStateException("Multiple APK outputs found, " +
                    "You must define the testApk to use for composer task on variant ${variant.name}", multipleFiles)
        } catch (noFiles: NoSuchElementException) {
            throw IllegalStateException("No APK output found," +
                    "You must define the testApk to use for composer task on variant ${variant.name}", noFiles)
        }
    }

    private fun apkForVariant(variant: ApplicationVariant): File {
        return variant.outputs.single().outputFile
    }

    private fun testApkForVariant(variant: ApplicationVariant): File {
        return variant.testVariant.outputs.single().outputFile
    }

    private fun getOutputDirectory(variant: ApplicationVariant, configurator: ConfiguratorDomainObj?, project: Project): File {
        return if (configurator == null || configurator.outputDirectory == ComposerTask.DEFAULT_OUTPUT_DIR) {
            File(project.buildDir, "reports/composer/${variant.name}")
        } else {
            configurator.outputDirectory
        }
    }

    private fun ApplicationVariant.testPackage(): String = "$applicationId.test"
}