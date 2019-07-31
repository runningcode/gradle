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
package org.gradle.integtests.resolve.forsubgraph

import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

class SubgraphVersionConstraintsIntegrationTest extends AbstractModuleDependencyResolveTest {

    def setup() {
        resolve.withStrictReasonsCheck()
    }

    void "can downgrade version"() {
        given:
        repository {
            'org:foo:1.0'()
            'org:foo:2.0'()
            'org:bar:1.0' {
                dependsOn 'org:foo:2.0'
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo:1.0') {
                   version { forSubgraph() }
                }
                conf('org:bar:1.0')
            }           
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:foo:1.0').byRequest()
                module('org:bar:1.0') {
                    edge('org:foo:2.0', 'org:foo:1.0').byAncestor()
                }
            }
        }
    }

    void "can use dependency constraint to downgrade version"() {
        given:
        repository {
            'org:foo:1.0'()
            'org:foo:2.0'()
            'org:bar:1.0' {
                dependsOn 'org:foo:2.0'
            }
        }

        buildFile << """
            dependencies {
                constraints {
                    conf('org:foo:1.0') {
                       version { forSubgraph() }
                    }
                }
                conf('org:bar:1.0')
            }           
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                constraint('org:foo:1.0')
                module('org:bar:1.0') {
                    edge('org:foo:2.0', 'org:foo:1.0').byConstraint().byAncestor()
                }
            }
        }
    }

    void "a forSubgraph constraint wins over a nested forSubgraph constraint"() {
        boolean publishedConstraintsSupported = gradleMetadataEnabled

        given:
        repository {
            'org:a:1.0' {
                dependsOn(group: 'org', artifact: 'b', version: '1.0')
                constraint(group: 'org', artifact: 'c', version: '2.0', forSubgraph: true)
            }
            'org:b:1.0' {
                dependsOn 'org:c:3.0'
            }
            'org:c:1.0'()
            'org:c:2.0'()
            'org:c:3.0'()
        }

        buildFile << """
            dependencies {
                conf('org:a:1.0')
                constraints {
                    conf('org:c:1.0') { version { forSubgraph() } }
                }
            }    
        """

        when:
        repositoryInteractions {
            'org:a:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:b:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:c:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:a:1.0') {
                    module('org:b:1.0') {
                        edge('org:c:3.0', 'org:c:1.0').byAncestor()
                    }
                    if (publishedConstraintsSupported) {
                        constraint('org:c:2.0', 'org:c:1.0')
                    }
                }
                constraint('org:c:1.0').byConstraint()
            }
        }
    }

    void "identical forSubgraph constraints can co-exist in a graph"() {
        boolean publishedConstraintsSupported = gradleMetadataEnabled

        given:
        repository {
            'org:a:1.0' {
                dependsOn(group: 'org', artifact: 'b', version: '1.0')
                constraint(group: 'org', artifact: 'c', version: '1.0', forSubgraph: true)
            }
            'org:b:1.0' {
                dependsOn 'org:c:2.0'
            }
            'org:c:1.0'()
            'org:c:2.0'()
        }

        buildFile << """
            dependencies {
                conf('org:a:1.0')
                constraints {
                    conf('org:c:1.0') { version { forSubgraph() } }
                }
            }    
        """

        when:
        repositoryInteractions {
            'org:a:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:b:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:c:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:a:1.0') {
                    module('org:b:1.0') {
                        edge('org:c:2.0', 'org:c:1.0').byAncestor()
                    }
                    if (publishedConstraintsSupported) {
                        constraint('org:c:1.0')
                    }
                }
                constraint('org:c:1.0').byConstraint()
            }
        }
    }

    void "conflicting version constraints are conflict resolved"() {
        boolean subgraphConstraintsSupported = gradleMetadataEnabled

        given:
        repository {
            'org:a:1.0' {
                dependsOn(group: 'org', artifact: 'b', version: '1.0')
                dependsOn(group: 'org', artifact: 'c', version: '1.0', forSubgraph: true)
            }
            'org:b:1.0' {
                dependsOn 'org:c:2.0'
            }
            'org:c:1.0'()
            'org:c:2.0'()
        }

        buildFile << """
            dependencies {
                conf('org:a:1.0')
                conf('org:c:2.0')
            }    
        """

        when:
        repositoryInteractions {
            'org:a:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:b:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:c:2.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:a:1.0') {
                    module('org:b:1.0') {
                        module('org:c:2.0')
                    }
                    edge('org:c:1.0', 'org:c:2.0').byConflictResolution("between versions 2.0 and 1.0").with {
                        if (subgraphConstraintsSupported) { it.byAncestor() }
                    }
                }
                module('org:c:2.0').byRequest()
            }
        }
    }

    void "forSubgraph is consumed from Gradle metadata"() {
        given:
        // If we do not use Gradle metadata, information is missing and we get a different result
        String cResult = gradleMetadataEnabled ? 'org:c:1.0' : 'org:c:2.0'

        repository {
            'org:a:1.0' {
                dependsOn(group: 'org', artifact: 'b', version: '1.0')
                dependsOn(group: 'org', artifact: 'c', version: '1.0', forSubgraph: true)
            }
            'org:b:1.0' {
                dependsOn 'org:c:2.0'
            }
            'org:c:1.0'()
            'org:c:2.0'()
        }

        buildFile << """
            dependencies {
                conf('org:a:1.0')
            }    
        """

        when:
        repositoryInteractions {
            'org:a:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:b:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            "$cResult" {
                expectGetMetadata()
                expectGetArtifact()
            }
            if (cResult == 'org:c:2.0') {
                'org:c:1.0' {
                    expectGetMetadata()
                }
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:a:1.0') {
                    module('org:b:1.0') {
                        edge('org:c:2.0', cResult)
                    }
                    edge('org:c:1.0', cResult).byRequest().with {
                        if (cResult == 'org:c:1.0') { it.byAncestor() } else { it.byConflictResolution("between versions 2.0 and 1.0") }
                    }
                }
            }
        }
    }

    void "forSubgraph from selected and later evicted modules are ignored"() {
        given:
        repository {
            'org:a:1.0' {
                dependsOn(group: 'org', artifact: 'b', version: '1.0')
                dependsOn(group: 'org', artifact: 'c', version: '1.0', forSubgraph: true)
            }
            'org:a:2.0' {
                dependsOn(group: 'org', artifact: 'b', version: '1.0')
                dependsOn(group: 'org', artifact: 'c', version: '1.0')
            }
            'org:b:1.0' {
                dependsOn 'org:c:2.0'
            }
            'org:c:1.0'()
            'org:c:2.0'()


            'org:x:1.0' {
                dependsOn 'org:y:1.0'
            }
            'org:y:1.0' {
                dependsOn 'org:a:2.0'
            }
        }

        buildFile << """
            dependencies {
                conf('org:a:1.0')
                conf('org:x:1.0')
            }    
        """

        when:
        repositoryInteractions {
            'org:a:2.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:b:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:c:2.0' {
                expectGetMetadata()
                expectGetArtifact()
            }

            'org:x:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:y:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }

            'org:a:1.0' {
                expectGetMetadata()
            }
            'org:c:1.0' {
                expectGetMetadata()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org:a:1.0', 'org:a:2.0') {
                    module('org:b:1.0') {
                        module('org:c:2.0').byRequest()
                    }
                    edge('org:c:1.0', 'org:c:2.0').byConflictResolution("between versions 2.0 and 1.0")
                }.byConflictResolution("between versions 2.0 and 1.0")
                module('org:x:1.0') {
                    module('org:y:1.0') {
                        module('org:a:2.0').byRequest()
                    }
                }
            }
        }
    }
}
