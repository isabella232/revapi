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
package org.revapi.jackson;

import static java.util.Collections.singletonList;

import java.net.URI;

import javax.annotation.Nullable;

import org.revapi.CompatibilityType;
import org.revapi.Difference;
import org.revapi.DifferenceSeverity;
import org.revapi.Report;
import org.revapi.base.BaseDifferenceAnalyzer;

public abstract class JacksonDifferenceAnalyzer<E extends JacksonElement<E>> extends BaseDifferenceAnalyzer<E> {

    @Override
    public void beginAnalysis(@Nullable E oldElement, @Nullable E newElement) {
    }

    @Override
    public Report endAnalysis(@Nullable E oldElement, @Nullable E newElement) {
        Report.Builder bld = Report.builder().withOld(oldElement).withNew(newElement);

        Difference.InReportBuilder dbld = null;
        String path = null;
        String file = null;
        if (oldElement == null) {
            dbld = bld.addDifference();
            addAdded(dbld);
            dbld.withDescription("The node was added.");
            path = newElement.getPath();
            file = newElement.getFilePath();
        } else if (newElement == null) {
            dbld = bld.addDifference();
            addRemoved(dbld);
            dbld.withDescription("The node was removed.");
            path = oldElement.getPath();
            file = oldElement.getFilePath();
        } else if ((oldElement.getNode().isValueNode() || newElement.getNode().isValueNode())
                && !oldElement.equals(newElement)) {
            // we're only reporting changes on value nodes
            dbld = bld.addDifference();
            addChanged(dbld);
            path = newElement.getPath();
            file = newElement.getFilePath();
            dbld.withDescription("The value changed from `" + oldElement.getValueString() + "` to `"
                    + newElement.getValueString() + "`.");
            dbld.addAttachment("oldValue", oldElement.getValueString());
            dbld.addAttachment("newValue", newElement.getValueString());
        }

        if (dbld != null) {
            dbld.addAttachment("file", file);
            dbld.addAttachment("path", path);
            dbld.withIdentifyingAttachments(singletonList("path"));
            dbld.done();
        }

        return bld.build();
    }

    protected void addRemoved(Difference.InReportBuilder bld) {
        String code = valueAddedCode();
        bld.withCode(valueRemovedCode()).withName("value removed").withDocumentationLink(documentationLinkForCode(code))
                .addClassification(CompatibilityType.SEMANTIC, DifferenceSeverity.BREAKING);
    }

    protected void addAdded(Difference.InReportBuilder bld) {
        String code = valueAddedCode();
        bld.withCode(code).withName("value added").withDocumentationLink(documentationLinkForCode(code))
                .addClassification(CompatibilityType.SEMANTIC, DifferenceSeverity.POTENTIALLY_BREAKING);
    }

    protected void addChanged(Difference.InReportBuilder bld) {
        String code = valueChangedCode();
        bld.withCode(code).withName("value changed").withDocumentationLink(documentationLinkForCode(code))
                .addClassification(CompatibilityType.SEMANTIC, DifferenceSeverity.POTENTIALLY_BREAKING);
    }

    protected abstract String valueRemovedCode();

    protected abstract String valueAddedCode();

    protected abstract String valueChangedCode();

    protected abstract @Nullable URI documentationLinkForCode(String code);
}
