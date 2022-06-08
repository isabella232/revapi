/*
 * Copyright 2014-2021 Lukas Krejci
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.revapi.java.model;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.Archive;
import org.revapi.Reference;
import org.revapi.base.BaseElement;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.TypeEnvironment;
import org.revapi.java.spi.UseSite;

/**
 * A common superclass for {@link AnnotationElement} and {@link JavaElementBase} to stuff that they share.
 */
public abstract class AbstractJavaElement extends BaseElement<JavaElement> implements JavaElement {
    protected final ProbingEnvironment environment;

    protected AbstractJavaElement(ProbingEnvironment environment) {
        super(environment.getApi());
        this.environment = environment;
    }

    @Override
    protected Reference<JavaElement> newReference(JavaElement target, Reference.Type<JavaElement> type) {
        return new UseSite((UseSite.Type) type, target);
    }

    @Nonnull
    @Override
    public TypeEnvironment getTypeEnvironment() {
        return environment;
    }

    public void setArchive(@Nullable Archive archive) {
        super.setArchive(archive);
    }
}
