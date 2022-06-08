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
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

import org.revapi.Archive;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.spi.JavaMethodElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
public final class MethodElement extends JavaElementBase<ExecutableElement, ExecutableType>
        implements JavaMethodElement {

    public static String createComparableSignature(ExecutableElement m, TypeMirror mType) {
        // the choice of '#' for a separator between the name and signature is because it precedes both '(' and any
        // legal character in a method name in the ASCII table
        return m.getSimpleName() + "#" + Util.toUniqueString(mType);
    }

    public MethodElement(ProbingEnvironment env, Archive archive, ExecutableElement element, ExecutableType type) {
        super(env, archive, element, type);
    }

    @SuppressWarnings("ConstantConditions")
    @Nonnull
    @Override
    public JavaTypeElement getParent() {
        return (JavaTypeElement) super.getParent();
    }

    public boolean isConstructor() {
        return getDeclaringElement().getKind() == ElementKind.CONSTRUCTOR;
    }

    @Nonnull
    @Override
    protected String getHumanReadableElementType() {
        return "method";
    }

    @Override
    protected String createComparableSignature() {
        return createComparableSignature(getDeclaringElement(), getModelRepresentation());
    }

    @Override
    public MethodElement clone() {
        return (MethodElement) super.clone();
    }
}
