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

package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.LocalDependencyFiles;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionLeafVisitor;

public class ResolvedFileCollectionVisitor extends ResolvedFilesCollectingVisitor {
    private final FileCollectionLeafVisitor visitor;

    public ResolvedFileCollectionVisitor(FileCollectionLeafVisitor visitor) {
        this.visitor = visitor;
    }

    @Override
    public FileCollectionLeafVisitor.VisitType prepareForVisit(FileCollectionInternal.Source source) {
        FileCollectionLeafVisitor.VisitType visitType = visitor.prepareForVisit(source);
        if (visitType == FileCollectionLeafVisitor.VisitType.Spec && source instanceof LocalDependencyFiles) {
            // This isn't quite right, when the local files are transformed. Should instead collect the inputs of artifact transforms
            // This collection is visited out of sequence
            ((LocalDependencyFiles) source).visitSpec(visitor);
        }
        return visitType;
    }

    @Override
    public void endVisitCollection(FileCollectionInternal.Source source) {
        visitor.visitCollection(source, getFiles());
        getFiles().clear();
    }
}
