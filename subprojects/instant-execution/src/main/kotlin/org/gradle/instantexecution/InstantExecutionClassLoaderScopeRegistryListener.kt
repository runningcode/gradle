/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.instantexecution

import org.gradle.initialization.ClassLoaderScopeRegistryListener
import org.gradle.initialization.DefaultClassLoaderScopeRegistry
import org.gradle.internal.classpath.ClassPath


internal
class InstantExecutionClassLoaderScopeRegistryListener(
    private val onDisable: (Any) -> Unit
) : ClassLoaderScopeRegistryListener {

    var coreAndPluginsSpec: ClassLoaderScopeSpec? = null

    private
    val scopeSpecs = mutableMapOf<String, ClassLoaderScopeSpec>()

    /**
     * Stops recording [ClassLoaderScopeSpec]s and releases any recorded state.
     */
    fun disable() {
        // TODO:instant-execution find a way to make `disable` unnecessary;
        //  maybe by introducing an `InstantExecutionBuildDefinition` service
        //  to replace DefaultInstantExecutionHost.
        coreAndPluginsSpec = null
        scopeSpecs.clear()
        onDisable(this)
    }

    override fun rootScopeCreated(scopeId: String) {
        if (scopeId === DefaultClassLoaderScopeRegistry.CORE_AND_PLUGINS_NAME) {
            ClassLoaderScopeSpec(scopeId).let { root ->
                coreAndPluginsSpec = root
                scopeSpecs[scopeId] = root
            }
        }
    }

    override fun childScopeCreated(parentId: String, childId: String) {
        scopeSpecs[parentId]?.let { scopeSpec ->
            ClassLoaderScopeSpec(childId).let { child ->
                scopeSpec.children.add(child)
                scopeSpecs[childId] = child
            }
        }
    }

    override fun localClasspathAdded(scopeId: String, localClassPath: ClassPath) {
        scopeSpecs[scopeId]?.localClassPath?.add(localClassPath)
    }

    override fun exportClasspathAdded(scopeId: String, exportClassPath: ClassPath) {
        scopeSpecs[scopeId]?.exportClassPath?.add(exportClassPath)
    }
}


internal
class ClassLoaderScopeSpec(val id: String) {
    val localClassPath = mutableListOf<ClassPath>()
    val exportClassPath = mutableListOf<ClassPath>()
    val children = mutableListOf<ClassLoaderScopeSpec>()
}
