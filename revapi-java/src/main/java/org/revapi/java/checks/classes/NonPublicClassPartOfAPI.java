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
package org.revapi.java.checks.classes;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
public final class NonPublicClassPartOfAPI extends CheckBase {

    private boolean reportUnchanged;

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        super.initialize(analysisContext);
        JsonNode reportUnchanged = analysisContext.getConfigurationNode().path("reportUnchanged");
        this.reportUnchanged = reportUnchanged.asBoolean(true);
    }

    @Nullable
    @Override
    public String getExtensionId() {
        return "nonPublicPartOfAPI";
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return new InputStreamReader(getClass().getResourceAsStream("/META-INF/nonPublicPartOfAPI-config-schema.json"),
                Charset.forName("UTF-8"));
    }

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.CLASS);
    }

    @Override
    public boolean isDescendingOnNonExisting() {
        return true;
    }

    @Override
    protected void doVisitClass(JavaTypeElement oldType, JavaTypeElement newType) {
        if (newType == null) {
            return;
        }

        if ((reportUnchanged || oldType == null) && newType.isInAPI() && !isAccessible(newType)
                && !isMissing(newType.getDeclaringElement())) {
            pushActive(oldType, newType);
        }
    }

    @Override
    protected List<Difference> doEnd() {
        ActiveElements<JavaTypeElement> types = popIfActive();

        if (types == null) {
            return null;
        }

        return Collections.singletonList(createDifferenceWithExplicitParams(Code.CLASS_NON_PUBLIC_PART_OF_API,
                Code.attachmentsFor(types.oldElement, types.newElement),
                Util.toHumanReadableString(types.newElement.getModelRepresentation())));
    }
}
