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
package org.revapi.java.transforms.methods;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;

import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.DifferenceTransform;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaMethodElement;

/**
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
public final class AnnotationTypeAttributeAdded implements DifferenceTransform<JavaMethodElement> {
    private Locale locale;
    private final Pattern[] codes;

    public AnnotationTypeAttributeAdded() {
        codes = new Pattern[] { Pattern.compile("^" + Pattern.quote(Code.METHOD_ABSTRACT_METHOD_ADDED.code()) + "$") };
    }

    @Nonnull
    @Override
    public Pattern[] getDifferenceCodePatterns() {
        return codes;
    }

    @Nullable
    @Override
    public String getExtensionId() {
        return "revapi.java.annotationTypeAttributeAdded";
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return null;
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        locale = analysisContext.getLocale();
    }

    @Nullable
    @Override
    public Difference transform(@Nullable JavaMethodElement oldElement, @Nullable JavaMethodElement newElement,
            @Nonnull Difference difference) {

        @SuppressWarnings("ConstantConditions")
        ExecutableElement method = newElement.getDeclaringElement();

        if (method.getEnclosingElement().getKind() == ElementKind.ANNOTATION_TYPE) {
            AnnotationValue defaultValue = method.getDefaultValue();

            if (defaultValue == null) {
                return Code.METHOD_ATTRIBUTE_WITH_NO_DEFAULT_ADDED_TO_ANNOTATION_TYPE.createDifference(locale,
                        new LinkedHashMap<>(difference.attachments));
            } else {
                return Code.METHOD_ATTRIBUTE_WITH_DEFAULT_ADDED_TO_ANNOTATION_TYPE.createDifference(locale,
                        new LinkedHashMap<>(difference.attachments));
            }
        }

        return difference;
    }

    @Override
    public void close() {
    }
}
