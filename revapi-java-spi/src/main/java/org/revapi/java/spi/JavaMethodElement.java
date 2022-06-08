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
package org.revapi.java.spi;

import javax.annotation.Nonnull;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.ExecutableType;

/**
 * Elements in the element forest that represent Java methods, will implement this interface.
 *
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
public interface JavaMethodElement extends JavaModelElement {
    @Override
    ExecutableType getModelRepresentation();

    @Override
    ExecutableElement getDeclaringElement();

    @Override
    @Nonnull
    JavaTypeElement getParent();
}
