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
package org.revapi.java.compilation;

import static org.revapi.java.model.MissingTypeElement.isMissing;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Types;

import org.revapi.java.model.MissingTypeElement;
import org.revapi.java.spi.IgnoreCompletionFailures;

/**
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
final class MissingTypeAwareDelegatingTypes implements Types {

    private final Types types;

    public MissingTypeAwareDelegatingTypes(Types types) {
        this.types = types;
    }

    @Override
    public Element asElement(final TypeMirror t) {
        return IgnoreCompletionFailures.in(checkMissing(types::asElement, null), t);
    }

    @Override
    public boolean isSameType(final TypeMirror t1, final TypeMirror t2) {
        return IgnoreCompletionFailures.in(checkMissing(types::isSameType, false), t1, t2);
    }

    @Override
    public boolean isSubtype(final TypeMirror t1, final TypeMirror t2) {
        return IgnoreCompletionFailures.in(checkMissing(types::isSubtype, false), t1, t2);
    }

    @Override
    public boolean isAssignable(final TypeMirror t1, final TypeMirror t2) {
        return IgnoreCompletionFailures.in(checkMissing(types::isAssignable, false), t1, t2);
    }

    @Override
    public boolean contains(final TypeMirror t1, final TypeMirror t2) {
        return IgnoreCompletionFailures.in(checkMissing(types::contains, false), t1, t2);
    }

    @Override
    public boolean isSubsignature(final ExecutableType m1, final ExecutableType m2) {
        return IgnoreCompletionFailures.in(checkMissing(types::isSubsignature, false), m1, m2);
    }

    @Override
    public List<? extends TypeMirror> directSupertypes(TypeMirror t) {

        return IgnoreCompletionFailures.in(checkMissing(types::directSupertypes, Collections.emptyList()), t);
    }

    @Override
    public TypeMirror erasure(TypeMirror t) {
        return IgnoreCompletionFailures.in(checkMissing(types::erasure, t), t);
    }

    @Override
    public TypeElement boxedClass(PrimitiveType p) {
        return IgnoreCompletionFailures.in(types::boxedClass, p);
    }

    @Override
    public PrimitiveType unboxedType(TypeMirror t) {
        if (isMissing(t)) {
            throw new IllegalArgumentException("Type " + t + " does not have an unboxing conversion.");
        }
        return IgnoreCompletionFailures.in(types::unboxedType, t);
    }

    @Override
    public TypeMirror capture(TypeMirror t) {
        return IgnoreCompletionFailures.in(checkMissing(types::capture, t), t);
    }

    @Override
    public PrimitiveType getPrimitiveType(TypeKind kind) {
        return types.getPrimitiveType(kind);
    }

    @Override
    public NullType getNullType() {
        return types.getNullType();
    }

    @Override
    public NoType getNoType(TypeKind kind) {
        return types.getNoType(kind);
    }

    @Override
    public ArrayType getArrayType(TypeMirror componentType) {
        if (isMissing(componentType)) {
            throw new IllegalArgumentException("Type " + componentType + " is not a valid component of an array.");
        }
        return IgnoreCompletionFailures.in(types::getArrayType, componentType);
    }

    @Override
    public WildcardType getWildcardType(TypeMirror extendsBound, TypeMirror superBound) {
        if (isMissing(extendsBound) || isMissing(superBound)) {
            throw new IllegalArgumentException("Invalid bounds.");
        }
        return IgnoreCompletionFailures.in(types::getWildcardType, extendsBound, superBound);
    }

    @Override
    public DeclaredType getDeclaredType(TypeElement typeElem, TypeMirror... typeArgs) {
        if (MissingTypeElement.isMissing(typeElem)) {
            throw new IllegalArgumentException("Invalid type element.");
        }
        for (TypeMirror t : typeArgs) {
            if (isMissing(t)) {
                throw new IllegalArgumentException("Invalid type arguments.");
            }
        }
        return IgnoreCompletionFailures.in(types::getDeclaredType, typeElem, typeArgs);
    }

    @Override
    public DeclaredType getDeclaredType(DeclaredType containing, TypeElement typeElem, TypeMirror... typeArgs) {
        if (isMissing(containing)) {
            throw new IllegalArgumentException("Invalid containing type.");
        }
        if (MissingTypeElement.isMissing(typeElem)) {
            throw new IllegalArgumentException("Invalid type element.");
        }
        for (TypeMirror t : typeArgs) {
            if (isMissing(t)) {
                throw new IllegalArgumentException("Invalid type arguments.");
            }
        }
        return IgnoreCompletionFailures.in(types::getDeclaredType, containing, typeElem, typeArgs);
    }

    @Override
    public TypeMirror asMemberOf(DeclaredType containing, Element element) {
        if (isMissing(containing)) {
            throw new IllegalArgumentException("Invalid containing type.");
        }
        if (MissingTypeElement.isMissing(element)) {
            throw new IllegalArgumentException("Invalid element.");
        }
        return IgnoreCompletionFailures.in(types::asMemberOf, containing, element);
    }

    private static <R, T extends TypeMirror> IgnoreCompletionFailures.Fn1<R, T> checkMissing(Function<T, R> fn,
            R returnValueOnMissing) {
        return (t) -> {
            if (isMissing(t)) {
                return returnValueOnMissing;
            } else {
                return fn.apply(t);
            }
        };
    }

    private static <R, T1 extends TypeMirror, T2 extends TypeMirror> IgnoreCompletionFailures.Fn2<R, T1, T2> checkMissing(
            BiFunction<T1, T2, R> fn, R returnValueOnMissing) {
        return (t1, t2) -> {
            if (isMissing(t1) || isMissing(t2)) {
                return returnValueOnMissing;
            } else {
                return fn.apply(t1, t2);
            }
        };
    }
}
