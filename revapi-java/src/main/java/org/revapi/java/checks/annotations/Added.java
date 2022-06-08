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
package org.revapi.java.checks.annotations;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
public final class Added extends CheckBase {
    @Override
    protected List<Difference> doVisitAnnotation(JavaAnnotationElement oldAnnotation,
            JavaAnnotationElement newAnnotation) {

        if (oldAnnotation == null && newAnnotation != null && isAccessible(newAnnotation.getParent())) {
            return Collections.singletonList(createDifference(Code.ANNOTATION_ADDED,
                    Code.attachmentsFor(null, newAnnotation.getParent(), "annotationType",
                            Util.toHumanReadableString(newAnnotation.getAnnotation().getAnnotationType()), "annotation",
                            Util.toHumanReadableString(newAnnotation.getAnnotation()))));
        }

        return null;
    }

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.ANNOTATION);
    }
}
