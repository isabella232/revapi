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
package org.revapi.java.checks.generics;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.lang.model.type.TypeMirror;

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
public class SuperTypeParametersChanged extends CheckBase {

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.CLASS);
    }

    @Override
    protected void doVisitClass(@Nullable JavaTypeElement oldType, @Nullable JavaTypeElement newType) {
        if (!isBothAccessible(oldType, newType)) {
            return;
        }

        assert oldType != null;
        assert newType != null;

        List<? extends TypeMirror> oldSuperTypes = getOldTypeEnvironment().getTypeUtils()
                .directSupertypes(oldType.getModelRepresentation());

        List<? extends TypeMirror> newSuperTypes = getNewTypeEnvironment().getTypeUtils()
                .directSupertypes(newType.getModelRepresentation());

        if (oldSuperTypes.size() != newSuperTypes.size()) {
            // super types changed, handled elsewhere
            return;
        }

        Map<String, TypeMirror> erasedOld = new LinkedHashMap<>();
        Map<String, TypeMirror> erasedNew = new LinkedHashMap<>();

        for (TypeMirror t : oldSuperTypes) {
            erasedOld.put(Util.toUniqueString(getOldTypeEnvironment().getTypeUtils().erasure(t)), t);
        }

        for (TypeMirror t : newSuperTypes) {
            erasedNew.put(Util.toUniqueString(getNewTypeEnvironment().getTypeUtils().erasure(t)), t);
        }

        if (!erasedOld.keySet().equals(erasedNew.keySet())) {
            // super types changed, handled elsewhere
            return;
        }

        Map<TypeMirror, TypeMirror> changed = new LinkedHashMap<>();

        for (Map.Entry<String, TypeMirror> e : erasedOld.entrySet()) {
            TypeMirror oldT = e.getValue();
            TypeMirror newT = erasedNew.get(e.getKey());
            String oldS = Util.toUniqueString(oldT);
            String newS = Util.toUniqueString(newT);

            if (!oldS.equals(newS)) {
                changed.put(oldT, newT);
            }
        }

        if (!changed.isEmpty()) {
            pushActive(oldType, newType, changed);
        }
    }

    @Nullable
    @Override
    protected List<Difference> doEnd() {
        ActiveElements<JavaTypeElement> types = popIfActive();
        if (types == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<TypeMirror, TypeMirror> changed = (Map<TypeMirror, TypeMirror>) types.context[0];

        List<Difference> ret = new ArrayList<>();
        for (Map.Entry<TypeMirror, TypeMirror> e : changed.entrySet()) {
            String oldS = Util.toHumanReadableString(e.getKey());
            String newS = Util.toHumanReadableString(e.getValue());
            ret.add(createDifference(Code.CLASS_SUPER_TYPE_TYPE_PARAMETERS_CHANGED, Code
                    .attachmentsFor(types.oldElement, types.newElement, "oldSuperType", oldS, "newSuperType", newS)));
        }

        return ret;
    }
}
