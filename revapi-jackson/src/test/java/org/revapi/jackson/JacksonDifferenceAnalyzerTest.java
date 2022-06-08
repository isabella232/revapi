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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.revapi.CompatibilityType.SEMANTIC;
import static org.revapi.DifferenceSeverity.BREAKING;
import static org.revapi.DifferenceSeverity.POTENTIALLY_BREAKING;

import java.net.URI;

import javax.annotation.Nullable;

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.revapi.API;
import org.revapi.Archive;
import org.revapi.Difference;
import org.revapi.Report;

class JacksonDifferenceAnalyzerTest {

    @Test
    void detectsAdditions() {
        Archive ar = Mockito.mock(Archive.class);
        API api = API.of(ar).build();

        TestElement element = new TestElement(api, ar, "file.js", JsonNodeFactory.instance.numberNode(42), 0);
        TestElement root = new TestElement(api, ar, "file.js", JsonNodeFactory.instance.arrayNode(), -1);
        root.getChildren().add(element);

        TestDifferenceAnalyzer analyzer = new TestDifferenceAnalyzer();

        analyzer.beginAnalysis(null, element);
        Report report = analyzer.endAnalysis(null, element);

        assertNull(report.getOldElement());
        assertSame(element, report.getNewElement());
        assertEquals(1, report.getDifferences().size());

        Difference diff = report.getDifferences().get(0);
        assertEquals("test.added", diff.code);

        assertEquals(1, diff.classification.size());
        assertEquals(POTENTIALLY_BREAKING, diff.classification.get(SEMANTIC));

        assertEquals(2, diff.attachments.size());
        assertEquals("file.js", diff.attachments.get("file"));
        assertEquals("/0", diff.attachments.get("path"));
    }

    @Test
    void detectsRemovals() {
        Archive ar = Mockito.mock(Archive.class);
        API api = API.of(ar).build();

        TestElement element = new TestElement(api, ar, "file.js", JsonNodeFactory.instance.numberNode(42), "here");
        TestElement root = new TestElement(api, ar, "file.js", JsonNodeFactory.instance.objectNode(), -1);
        root.getChildren().add(element);

        TestDifferenceAnalyzer analyzer = new TestDifferenceAnalyzer();

        analyzer.beginAnalysis(element, null);
        Report report = analyzer.endAnalysis(element, null);

        assertSame(element, report.getOldElement());
        assertNull(report.getNewElement());
        assertEquals(1, report.getDifferences().size());

        Difference diff = report.getDifferences().get(0);
        assertEquals("test.removed", diff.code);

        assertEquals(1, diff.classification.size());
        assertEquals(BREAKING, diff.classification.get(SEMANTIC));

        assertEquals(2, diff.attachments.size());
        assertEquals("file.js", diff.attachments.get("file"));
        assertEquals("/here", diff.attachments.get("path"));
    }

    @Test
    void detectsChanges() {
        Archive ar = Mockito.mock(Archive.class);
        API api = API.of(ar).build();

        TestElement oldEl = new TestElement(api, ar, "file.js", JsonNodeFactory.instance.numberNode(42), "here");
        TestElement newEl = new TestElement(api, ar, "file.js", JsonNodeFactory.instance.textNode("different"), "here");
        TestElement oldRoot = new TestElement(api, ar, "file.js", JsonNodeFactory.instance.arrayNode(), -1);
        oldRoot.getChildren().add(oldEl);
        TestElement newRoot = new TestElement(api, ar, "file.js", JsonNodeFactory.instance.arrayNode(), -1);
        newRoot.getChildren().add(newEl);

        TestDifferenceAnalyzer analyzer = new TestDifferenceAnalyzer();

        analyzer.beginAnalysis(oldEl, newEl);
        Report report = analyzer.endAnalysis(oldEl, newEl);

        assertSame(oldEl, report.getOldElement());
        assertSame(newEl, report.getNewElement());
        assertEquals(1, report.getDifferences().size());

        Difference diff = report.getDifferences().get(0);
        assertEquals("test.changed", diff.code);

        assertEquals(1, diff.classification.size());
        assertEquals(POTENTIALLY_BREAKING, diff.classification.get(SEMANTIC));

        assertEquals(4, diff.attachments.size());
        assertEquals("file.js", diff.attachments.get("file"));
        assertEquals("/here", diff.attachments.get("path"));
        assertEquals("42", diff.attachments.get("oldValue"));
        assertEquals("different", diff.attachments.get("newValue"));
    }

    @Test
    void doesntReportArraysAsWhole() {
        Archive ar = Mockito.mock(Archive.class);
        API api = API.of(ar).build();

        TestElement oldEl = new TestElement(api, ar, "file.js", JsonNodeFactory.instance.arrayNode().add(1), "here");
        TestElement newEl = new TestElement(api, ar, "file.js", JsonNodeFactory.instance.arrayNode().add(0).add(1),
                "here");

        TestDifferenceAnalyzer analyzer = new TestDifferenceAnalyzer();

        analyzer.beginAnalysis(oldEl, newEl);
        Report report = analyzer.endAnalysis(oldEl, newEl);

        assertSame(oldEl, report.getOldElement());
        assertSame(newEl, report.getNewElement());
        assertTrue(report.getDifferences().isEmpty());
    }

    @Test
    void doesntReportObjectsAsWhole() {
        Archive ar = Mockito.mock(Archive.class);
        API api = API.of(ar).build();

        TestElement oldEl = new TestElement(api, ar, "file.js", JsonNodeFactory.instance.objectNode().put("a", 1),
                "here");
        TestElement newEl = new TestElement(api, ar, "file.js", JsonNodeFactory.instance.objectNode().put("b", 2),
                "here");

        TestDifferenceAnalyzer analyzer = new TestDifferenceAnalyzer();

        analyzer.beginAnalysis(oldEl, newEl);
        Report report = analyzer.endAnalysis(oldEl, newEl);

        assertSame(oldEl, report.getOldElement());
        assertSame(newEl, report.getNewElement());
        assertTrue(report.getDifferences().isEmpty());
    }

    public static final class TestElement extends JacksonElement<TestElement> {
        public TestElement(API api, Archive archive, String filePath, TreeNode node, String key) {
            super(api, archive, filePath, node, key);
        }

        public TestElement(API api, Archive archive, String filePath, TreeNode node, int index) {
            super(api, archive, filePath, node, index);
        }
    }

    public static final class TestDifferenceAnalyzer extends JacksonDifferenceAnalyzer<TestElement> {

        @Override
        protected String valueRemovedCode() {
            return "test.removed";
        }

        @Override
        protected String valueAddedCode() {
            return "test.added";
        }

        @Override
        protected String valueChangedCode() {
            return "test.changed";
        }

        @Nullable
        @Override
        protected URI documentationLinkForCode(String code) {
            return null;
        }
    }
}
