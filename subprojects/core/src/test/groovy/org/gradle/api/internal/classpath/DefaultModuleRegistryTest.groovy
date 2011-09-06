/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.classpath

import spock.lang.Specification
import org.junit.Rule
import org.gradle.util.TemporaryFolder
import org.gradle.util.TestFile

class DefaultModuleRegistryTest extends Specification {
    @Rule final TemporaryFolder tmpDir = new TemporaryFolder()
    TestFile runtimeDep
    TestFile resourcesDir
    TestFile jarFile
    TestFile distDir

    def setup() {
        def emptyDir = tmpDir.createDir("empty")
        emptyDir.file('readme.txt').createFile()
        distDir = tmpDir.createDir("dist")

        distDir.createDir("lib")
        distDir.createDir("lib/plugins")
        distDir.createDir("lib/core-impl")
        runtimeDep = distDir.file("lib/dep-1.2.jar")
        emptyDir.zipTo(runtimeDep)

        resourcesDir = tmpDir.createDir("classes")
        def properties = new Properties()
        properties.runtime = 'dep-1.2.jar'
        resourcesDir.file("gradle-some-module-classpath.properties").withOutputStream { outstr -> properties.save(outstr, "header") }

        jarFile = distDir.file("lib/gradle-some-module-5.1.jar")
        resourcesDir.zipTo(jarFile)
    }

    def "uses manifest from classpath when run from IDEA"() {
        given:
        def classesDir = tmpDir.createDir("out/production/someModule")
        def staticResourcesDir = tmpDir.createDir("some-module/src/main/resources")
        def ignoredDir = tmpDir.createDir("ignore-me-out/production/someModule")
        def cl = new URLClassLoader([ignoredDir, classesDir, resourcesDir, staticResourcesDir, runtimeDep].collect { it.toURI().toURL() } as URL[])
        def registry = new DefaultModuleRegistry(cl, distDir)

        expect:
        def module = registry.getModule("gradle-some-module")
        module.implementationClasspath as List == [classesDir, staticResourcesDir, resourcesDir]
        module.runtimeClasspath as List == [runtimeDep]
    }

    def "uses manifest from classpath when run from Eclipse"() {
        given:
        def classesDir = tmpDir.createDir("some-module/bin")
        def staticResourcesDir = tmpDir.createDir("some-module/src/main/resources")
        def cl = new URLClassLoader([classesDir, resourcesDir, staticResourcesDir, runtimeDep].collect { it.toURI().toURL() } as URL[])
        def registry = new DefaultModuleRegistry(cl, distDir)

        expect:
        def module = registry.getModule("gradle-some-module")
        module.implementationClasspath as List == [classesDir, staticResourcesDir, resourcesDir]
        module.runtimeClasspath as List == [runtimeDep]
    }

    def "uses manifest from classpath when run from build"() {
        given:
        def classesDir = tmpDir.createDir("some-module/build/classes/main")
        def staticResourcesDir = tmpDir.createDir("some-module/build/resources/main")
        def cl = new URLClassLoader([classesDir, resourcesDir, staticResourcesDir, runtimeDep].collect { it.toURI().toURL() } as URL[])
        def registry = new DefaultModuleRegistry(cl, distDir)

        expect:
        def module = registry.getModule("gradle-some-module")
        module.implementationClasspath as List == [classesDir, staticResourcesDir, resourcesDir]
        module.runtimeClasspath as List == [runtimeDep]
    }

    def "uses manifest from a jar on the classpath"() {
        given:
        def cl = new URLClassLoader([jarFile, runtimeDep].collect { it.toURI().toURL() } as URL[])
        def registry = new DefaultModuleRegistry(cl, distDir)

        expect:
        def module = registry.getModule("gradle-some-module")
        module.implementationClasspath as List == [jarFile]
        module.runtimeClasspath as List == [runtimeDep]
    }

    def "uses manifest from jar in distribution image when not available on classpath"() {
        given:
        def cl = new URLClassLoader([] as URL[])
        def registry = new DefaultModuleRegistry(cl, distDir)

        expect:
        def module = registry.getModule("gradle-some-module")
        module.implementationClasspath as List == [jarFile]
        module.runtimeClasspath as List == [runtimeDep]
    }

    def "handles empty runtime classpath in manifest"() {
        given:
        def properties = new Properties()
        properties.runtime = ''
        resourcesDir.file("gradle-some-module-classpath.properties").withOutputStream { outstr -> properties.save(outstr, "header") }

        def cl = new URLClassLoader([resourcesDir, runtimeDep].collect { it.toURI().toURL() } as URL[])
        def registry = new DefaultModuleRegistry(cl, distDir)

        expect:
        def module = registry.getModule("gradle-some-module")
        module.runtimeClasspath.empty
    }

    def "fails when classpath does not contain manifest"() {
        given:
        def cl = new URLClassLoader([] as URL[])
        def registry = new DefaultModuleRegistry(cl, null)

        when:
        registry.getModule("gradle-some-module")

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot locate classpath manifest 'gradle-some-module-classpath.properties' in classpath."
    }

    def "fails when classpath and distribution image do not contain manifest"() {
        given:
        def cl = new URLClassLoader([] as URL[])
        def registry = new DefaultModuleRegistry(cl, distDir)

        when:
        registry.getModule("gradle-other-module")

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot locate JAR for module 'gradle-other-module' in distribution directory '$distDir'."
    }

    def "locates an external module as a JAR on the classpath"() {
        given:
        def cl = new URLClassLoader([runtimeDep].collect { it.toURI().toURL() } as URL[])
        def registry = new DefaultModuleRegistry(cl, distDir)

        expect:
        def module = registry.getExternalModule("dep")
        module.implementationClasspath as List == [runtimeDep]
        module.runtimeClasspath.empty
    }
    
    def "locates an external module as a JAR in the distribution image when not available on the classpath"() {
        given:
        def cl = new URLClassLoader([] as URL[])
        def registry = new DefaultModuleRegistry(cl, distDir)

        expect:
        def module = registry.getExternalModule("dep")
        module.implementationClasspath as List == [runtimeDep]
        module.runtimeClasspath.empty
    }
}
