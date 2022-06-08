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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaTypeElement;

/**
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
public final class Missing extends CheckBase {

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.CLASS);
    }

    @Override
    public boolean isDescendingOnNonExisting() {
        return true;
    }

    @Override
    protected void doVisitClass(@Nullable JavaTypeElement oldType, @Nullable JavaTypeElement newType) {
        boolean oldMissing = oldType != null && isMissing(oldType.getDeclaringElement());
        boolean oldInApi = oldType != null && oldType.isInAPI();
        boolean newMissing = newType != null && isMissing(newType.getDeclaringElement());
        boolean newInApi = newType != null && newType.isInAPI();

        if ((oldMissing || newMissing) && (oldInApi || newInApi)) {
            pushActive(oldType, newType);
        }
    }

    @Nullable
    @Override
    protected List<Difference> doEnd() {
        ActiveElements<JavaTypeElement> types = popIfActive();
        if (types == null) {
            return null;
        }

        List<Difference> ret = new ArrayList<>();

        if (types.oldElement != null) {
            ret.add(createDifferenceWithExplicitParams(Code.MISSING_IN_OLD_API,
                    Code.attachmentsFor(types.oldElement, types.newElement),
                    types.oldElement.getDeclaringElement().getQualifiedName().toString()));
        }

        if (types.newElement != null) {
            ret.add(createDifferenceWithExplicitParams(Code.MISSING_IN_NEW_API,
                    Code.attachmentsFor(types.oldElement, types.newElement),
                    types.newElement.getDeclaringElement().getQualifiedName().toString()));
        }

        return ret;
    }
}
