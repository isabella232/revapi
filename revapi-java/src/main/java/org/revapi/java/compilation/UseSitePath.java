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

import java.util.Objects;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

public class UseSitePath {
    public final TypeElement owner;
    public final Element useSite;

    UseSitePath(TypeElement owner, Element useSite) {
        this.owner = owner;
        this.useSite = useSite;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        UseSitePath that = (UseSitePath) o;
        return owner.equals(that.owner) && useSite.equals(that.useSite);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, useSite);
    }
}
