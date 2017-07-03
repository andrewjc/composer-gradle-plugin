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

import groovy.lang.Closure
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project

open class ConfigExtension(project: Project) {
    val configs: NamedDomainObjectContainer<ConfiguratorDomainObj> =
            project.container(ConfiguratorDomainObj::class.java)

    val variants = mutableListOf<String>()

    val instrumentationArguments: MutableMap<String, String> = mutableMapOf()

    fun configs(closure: Closure<Any>) {
        configs.configure(closure)
    }

    fun variants(vararg values: String) {
        variants.addAll(values)
    }

    fun instrumentationArguments(vararg values: Pair<String, String>) {
        instrumentationArguments.putAll(values)
    }

    fun instrumentationArguments(values: Map<String, String>) {
        instrumentationArguments.putAll(values)
    }
}