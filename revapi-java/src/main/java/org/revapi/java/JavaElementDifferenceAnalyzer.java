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
package org.revapi.java;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;

import java.text.MessageFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;

import javax.annotation.Nullable;
import javax.lang.model.type.DeclaredType;

import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.DifferenceAnalyzer;
import org.revapi.Reference;
import org.revapi.Report;
import org.revapi.Stats;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.model.AnnotationElement;
import org.revapi.java.model.FieldElement;
import org.revapi.java.model.MethodElement;
import org.revapi.java.model.MethodParameterElement;
import org.revapi.java.model.TypeElement;
import org.revapi.java.spi.Check;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.UseSite;
import org.revapi.java.spi.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
public final class JavaElementDifferenceAnalyzer implements DifferenceAnalyzer<JavaElement> {
    private static final Logger LOG = LoggerFactory.getLogger(JavaElementDifferenceAnalyzer.class);

    private static final Map<Check.Type, Set<Check.Type>> POSSIBLE_CHILDREN_TYPES;

    static {
        Map<Check.Type, Set<Check.Type>> map = new EnumMap<>(Check.Type.class);
        map.put(Check.Type.ANNOTATION, emptySet());
        map.put(Check.Type.CLASS, EnumSet.of(Check.Type.CLASS, Check.Type.FIELD, Check.Type.METHOD,
                Check.Type.METHOD_PARAMETER, Check.Type.ANNOTATION));
        map.put(Check.Type.FIELD, singleton(Check.Type.ANNOTATION));
        map.put(Check.Type.METHOD, EnumSet.of(Check.Type.METHOD_PARAMETER, Check.Type.ANNOTATION));
        map.put(Check.Type.METHOD_PARAMETER, singleton(Check.Type.ANNOTATION));
        POSSIBLE_CHILDREN_TYPES = Collections.unmodifiableMap(map);
    }

    private final AnalysisConfiguration analysisConfiguration;
    private final ResourceBundle messages;
    private final ProbingEnvironment oldEnvironment;
    private final ProbingEnvironment newEnvironment;
    private final Map<Check.Type, List<Check>> checksByInterest;
    private final Deque<Collection<Check>> checksStack = new ArrayDeque<>();

    // NOTE: this doesn't have to be a stack of lists only because of the fact that annotations
    // are always sorted as last amongst sibling model elements.
    // So, when reported for their parent element, we can be sure that there are no more children
    // coming for given parent.
    private List<Difference> lastAnnotationResults;
    // if one of the checked elements is null, we might enter a different check mode where only checks that require
    // descending on non-existing elements are used for both speed and correctness reasons. For it to work correctly
    // we need to track where did we enter this special mode.
    private boolean nonExistenceMode;
    private JavaElement nonExistenceOldRoot;
    private JavaElement nonExistenceNewRoot;

    private final Map<Check.Type, Set<Check>> descendingChecksByTypes;

    public JavaElementDifferenceAnalyzer(AnalysisContext analysisContext, ProbingEnvironment oldEnvironment,
            ProbingEnvironment newEnvironment, Iterable<Check> checks, AnalysisConfiguration analysisConfiguration) {

        this.descendingChecksByTypes = new HashMap<>();

        for (Check c : checks) {
            c.setOldTypeEnvironment(oldEnvironment);
            c.setNewTypeEnvironment(newEnvironment);
            if (c.isDescendingOnNonExisting()) {
                c.getInterest().forEach(t -> descendingChecksByTypes.computeIfAbsent(t, __ -> new HashSet<>()).add(c));
            }
        }

        this.analysisConfiguration = analysisConfiguration;

        messages = ResourceBundle.getBundle("org.revapi.java.messages", analysisContext.getLocale());

        this.oldEnvironment = oldEnvironment;
        this.newEnvironment = newEnvironment;

        this.checksByInterest = new EnumMap<>(Check.Type.class);
        checks.forEach(
                c -> c.getInterest().forEach(i -> checksByInterest.computeIfAbsent(i, __ -> new ArrayList<>()).add(c)));
    }

    @Override
    public void open() {
        Timing.LOG.debug("Opening difference analyzer.");
    }

    @Override
    public void close() {
        Timing.LOG.debug("Difference analyzer closed.");
    }

    @Override
    public void beginAnalysis(@Nullable JavaElement oldElement, @Nullable JavaElement newElement) {
        Timing.LOG.trace("Beginning analysis of {} and {}.", oldElement, newElement);

        Check.Type elementsType = getCheckType(oldElement, newElement);
        Collection<Check> possibleChecks = nonExistenceMode
                ? descendingChecksByTypes.getOrDefault(elementsType, emptySet()) : checksByInterest.get(elementsType);

        if (conforms(oldElement, newElement, TypeElement.class)) {
            checksStack.push(possibleChecks);
            lastAnnotationResults = null;
            for (Check c : possibleChecks) {
                Stats.of(c.getClass().getName()).start();
                c.visitClass(oldElement == null ? null : (TypeElement) oldElement,
                        newElement == null ? null : (TypeElement) newElement);
                Stats.of(c.getClass().getName()).end(oldElement, newElement);
            }
        } else if (conforms(oldElement, newElement, AnnotationElement.class)) {
            // annotation are always terminal elements and they also always sort as last elements amongst siblings, so
            // treat them a bit differently
            if (lastAnnotationResults == null) {
                lastAnnotationResults = new ArrayList<>(4);
            }
            // DO NOT push the ANNOTATION type to the checkTypeStack nor push the applied checks to the checksStack.
            // Annotations are handled differently and this would lead to the stack corruption and missed problems!!!
            for (Check c : possibleChecks) {
                Stats.of(c.getClass().getName()).start();
                List<Difference> cps = c.visitAnnotation(oldElement == null ? null : (AnnotationElement) oldElement,
                        newElement == null ? null : (AnnotationElement) newElement);
                if (cps != null) {
                    lastAnnotationResults.addAll(cps);
                }
                Stats.of(c.getClass().getName()).end(oldElement, newElement);
            }
        } else if (conforms(oldElement, newElement, FieldElement.class)) {
            doRestrictedCheck((FieldElement) oldElement, (FieldElement) newElement, Check.Type.FIELD, possibleChecks);
        } else if (conforms(oldElement, newElement, MethodElement.class)) {
            doRestrictedCheck((MethodElement) oldElement, (MethodElement) newElement, Check.Type.METHOD,
                    possibleChecks);
        } else if (conforms(oldElement, newElement, MethodParameterElement.class)) {
            doRestrictedCheck((MethodParameterElement) oldElement, (MethodParameterElement) newElement,
                    Check.Type.METHOD_PARAMETER, possibleChecks);
        }

        if (!nonExistenceMode && (oldElement == null || newElement == null)) {
            nonExistenceMode = true;
            nonExistenceOldRoot = oldElement;
            nonExistenceNewRoot = newElement;
        }
    }

    @Override
    public boolean isDescendRequired(@Nullable JavaElement oldElement, @Nullable JavaElement newElement) {
        if (oldElement != null && newElement != null) {
            return true;
        }

        Check.Type type = getCheckType(oldElement, newElement);

        if (type == null) {
            return false;
        }

        Set<Check.Type> possibleChildren = POSSIBLE_CHILDREN_TYPES.get(type);

        return descendingChecksByTypes.keySet().stream().anyMatch(possibleChildren::contains);
    }

    private <T extends JavaModelElement> void doRestrictedCheck(T oldElement, T newElement, Check.Type interest,
            Collection<Check> possibleChecks) {
        lastAnnotationResults = null;

        if (!(isCheckedElsewhere(oldElement, oldEnvironment) && isCheckedElsewhere(newElement, newEnvironment))) {
            checksStack.push(possibleChecks);
            for (Check c : possibleChecks) {
                Stats.of(c.getClass().getName()).start();
                switch (interest) {
                case FIELD:
                    c.visitField((FieldElement) oldElement, (FieldElement) newElement);
                    break;
                case METHOD:
                    c.visitMethod((MethodElement) oldElement, (MethodElement) newElement);
                    break;
                case METHOD_PARAMETER:
                    c.visitMethodParameter((MethodParameterElement) oldElement, (MethodParameterElement) newElement);
                    break;
                }
                Stats.of(c.getClass().getName()).end(oldElement, newElement);
            }
        } else {
            // "ignore what's on the stack because no checks actually happened".
            checksStack.push(emptyList());
        }
    }

    @Override
    public Report endAnalysis(@Nullable JavaElement oldElement, @Nullable JavaElement newElement) {
        if (oldElement == nonExistenceOldRoot && newElement == nonExistenceNewRoot) {
            nonExistenceMode = false;
            nonExistenceOldRoot = null;
            nonExistenceNewRoot = null;
        }

        if (conforms(oldElement, newElement, AnnotationElement.class)) {
            // the annotations are always reported at the parent element
            return new Report(Collections.emptyList(), oldElement, newElement);
        }

        List<Difference> differences = new ArrayList<>();
        Collection<Check> lastChecks = checksStack.pop();

        for (Check c : lastChecks) {
            List<Difference> p = c.visitEnd();
            if (p != null) {
                differences.addAll(p);
            }
        }

        if (lastAnnotationResults != null && !lastAnnotationResults.isEmpty()) {
            differences.addAll(lastAnnotationResults);
            lastAnnotationResults.clear();
        }

        if (!differences.isEmpty()) {
            LOG.trace("Detected following problems: {}", differences);
        }
        Timing.LOG.trace("Ended analysis of {} and {}.", oldElement, newElement);

        ListIterator<Difference> it = differences.listIterator();
        while (it.hasNext()) {
            Difference d = it.next();
            if (analysisConfiguration.reportUseForAllDifferences()
                    || analysisConfiguration.getUseReportingCodes().contains(d.code)) {
                StringBuilder oldUseChain = null;
                StringBuilder newUseChain = null;

                if (oldElement != null) {
                    oldUseChain = new StringBuilder();
                    appendUses(oldEnvironment, oldElement, oldUseChain);
                }

                if (newElement != null) {
                    newUseChain = new StringBuilder();
                    appendUses(newEnvironment, newElement, newUseChain);
                }

                Map<String, String> atts = new HashMap<>(d.attachments);
                if (oldUseChain != null) {
                    atts.put("exampleUseChainInOldApi", oldUseChain.toString());
                }
                if (newUseChain != null) {
                    atts.put("exampleUseChainInNewApi", newUseChain.toString());
                }

                d = Difference.builder().addAttachments(atts).addClassifications(d.classification).withCode(d.code)
                        .withName(d.name).withDescription(d.description).build();
            }
            it.set(d);
        }

        return new Report(differences, oldElement, newElement);
    }

    private <T> boolean conforms(Object a, Object b, Class<T> cls) {
        boolean ca = a == null || cls.isAssignableFrom(a.getClass());
        boolean cb = b == null || cls.isAssignableFrom(b.getClass());

        return ca && cb;
    }

    private Check.Type getCheckType(JavaElement a, JavaElement b) {
        if (a != null) {
            return getCheckType(a);
        } else if (b != null) {
            return getCheckType(b);
        } else {
            return null;
        }
    }

    private Check.Type getCheckType(JavaElement e) {
        if (e instanceof TypeElement) {
            return Check.Type.CLASS;
        } else if (e instanceof AnnotationElement) {
            return Check.Type.ANNOTATION;
        } else if (e instanceof FieldElement) {
            return Check.Type.FIELD;
        } else if (e instanceof MethodElement) {
            return Check.Type.METHOD;
        } else if (e instanceof MethodParameterElement) {
            return Check.Type.METHOD_PARAMETER;
        } else {
            return null;
        }
    }

    private void append(StringBuilder bld, TypeAndUseSite typeAndUseSite) {
        String message;
        switch (typeAndUseSite.useSite.getType()) {
        case ANNOTATES:
            message = "revapi.java.uses.annotates";
            break;
        case HAS_TYPE:
            message = "revapi.java.uses.hasType";
            break;
        case IS_IMPLEMENTED:
            message = "revapi.java.uses.isImplemented";
            break;
        case IS_INHERITED:
            message = "revapi.java.uses.isInherited";
            break;
        case IS_THROWN:
            message = "revapi.java.uses.isThrown";
            break;
        case PARAMETER_TYPE:
            message = "revapi.java.uses.parameterType";
            break;
        case RETURN_TYPE:
            message = "revapi.java.uses.returnType";
            break;
        case CONTAINS:
            message = "revapi.java.uses.contains";
            break;
        case TYPE_PARAMETER_OR_BOUND:
            message = "revapi.java.uses.typeParameterOrBound";
            break;
        default:
            throw new AssertionError("Invalid use type: " + typeAndUseSite.useSite.getType());
        }

        message = messages.getString(message);
        message = MessageFormat.format(message, typeAndUseSite.useSite.getElement().getFullHumanReadableString(),
                Util.toHumanReadableString(typeAndUseSite.type));

        bld.append(message);
    }

    private void appendUses(ProbingEnvironment env, JavaElement element, final StringBuilder bld) {
        LOG.trace("Reporting uses of {}", element);

        if (element == null) {
            bld.append("<null>");
            return;
        }

        while (element != null && !(element instanceof JavaTypeElement)) {
            element = element.getParent();
        }

        if (element == null) {
            return;
        }

        JavaTypeElement usedType = (JavaTypeElement) element;

        if (usedType.isInAPI() && !usedType.isInApiThroughUse()) {
            String message = MessageFormat.format(messages.getString("revapi.java.uses.partOfApi"),
                    usedType.getFullHumanReadableString());
            bld.append(message);
            return;
        }

        for (Reference<JavaElement> ref : usedType.getReferencingElements()) {
            if (appendUse(env, usedType, bld, usedType.getModelRepresentation(), (UseSite) ref)) {
                break;
            }
        }
    }

    private boolean appendUse(ProbingEnvironment env, JavaTypeElement usedType, StringBuilder bld, DeclaredType type,
            UseSite use) {

        if (!use.getType().isMovingToApi()) {
            return false;
        }

        List<TypeAndUseSite> chain = getExamplePathToApiArchive(env, usedType, type, use);

        if (chain.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Could not find example path to API element for type {} starting with use {}",
                        ((javax.lang.model.element.TypeElement) type.asElement()).getQualifiedName().toString(), use);
            }
            return false;
        }

        Iterator<TypeAndUseSite> chainIt = chain.iterator();

        TypeAndUseSite last = chainIt.next();
        append(bld, last);

        while (chainIt.hasNext()) {
            bld.append(" <- ");
            last = chainIt.next();
            append(bld, last);
        }

        String message = MessageFormat.format(messages.getString("revapi.java.uses.partOfApi"),
                last.useSite.getElement().getFullHumanReadableString());

        bld.append(" (").append(message).append(")");

        return true;
    }

    private List<TypeAndUseSite> getExamplePathToApiArchive(ProbingEnvironment env, JavaTypeElement usedType,
            DeclaredType type, UseSite bottomUse) {

        ArrayList<TypeAndUseSite> ret = new ArrayList<>();

        traverseToApi(env, usedType, type, bottomUse, ret, new HashSet<>());

        return ret;
    }

    private boolean traverseToApi(ProbingEnvironment env, final JavaTypeElement usedType, final DeclaredType type,
            final UseSite currentUse, final List<TypeAndUseSite> path,
            final Set<javax.lang.model.element.TypeElement> visitedTypes) {

        if (!currentUse.getType().isMovingToApi()) {
            return false;
        }

        JavaTypeElement ut = findClassOf(currentUse.getElement());

        return appendUseType(env, ut, path, usedType, type, currentUse, visitedTypes);
    }

    private boolean appendUseType(ProbingEnvironment env, JavaTypeElement ut, List<TypeAndUseSite> path,
            JavaTypeElement usedType, DeclaredType type, UseSite currentUse,
            Set<javax.lang.model.element.TypeElement> visitedTypes) {

        javax.lang.model.element.TypeElement useType = ut.getDeclaringElement();

        if (visitedTypes.contains(useType)) {
            return false;
        }

        visitedTypes.add(useType);

        if (ut.isInAPI() && !ut.isInApiThroughUse() && !ut.equals(usedType)) {
            // the class is in the primary API
            path.add(0, new TypeAndUseSite(type, currentUse));
            return true;
        } else {

            boolean ret = false;
            for (Reference<JavaElement> ref : ut.getReferencingElements()) {
                if (traverseToApi(env, ut, ut.getModelRepresentation(), (UseSite) ref, path, visitedTypes)) {
                    path.add(0, new TypeAndUseSite(type, currentUse));
                    ret = true;
                    break;
                }
            }

            if (!ret) {
                Set<javax.lang.model.element.TypeElement> derivedUseTypes = env.getDerivedTypes(useType);

                for (javax.lang.model.element.TypeElement dut : derivedUseTypes) {
                    TypeElement model = env.getTypeMap().get(dut);
                    if (model == null) {
                        continue;
                    }

                    JavaModelElement derivedUseElement = findSameDeclarationUnder(currentUse.getElement(), model);
                    if (derivedUseElement == null) {
                        continue;
                    }

                    UseSite derivedUse = new UseSite(currentUse.getType(), derivedUseElement);

                    if (appendUseType(env, model, path, usedType, type, derivedUse, visitedTypes)) {

                        ret = true;
                        break;
                    }
                }

            }

            return ret;
        }
    }

    private JavaTypeElement findClassOf(JavaElement element) {
        while (element != null && !(element instanceof JavaTypeElement)) {
            element = element.getParent();
        }

        return (JavaTypeElement) element;
    }

    private javax.lang.model.element.TypeElement findTypeOf(javax.lang.model.element.Element element) {
        while (element != null && !(element.getKind().isClass() || element.getKind().isInterface())) {
            element = element.getEnclosingElement();
        }

        return (javax.lang.model.element.TypeElement) element;
    }

    private JavaModelElement findSameDeclarationUnder(JavaElement declaredElement, JavaTypeElement owningElement) {
        if (!(declaredElement instanceof JavaModelElement)) {
            return null;
        }

        JavaModelElement dme = (JavaModelElement) declaredElement;
        for (JavaElement e : owningElement.getChildren()) {
            if (e instanceof JavaModelElement
                    && ((JavaModelElement) e).getDeclaringElement().equals(dme.getDeclaringElement())) {
                return (JavaModelElement) e;
            }
        }

        return null;
    }

    private boolean isCheckedElsewhere(JavaModelElement element, ProbingEnvironment env) {
        if (element == null) {
            // the other element will not be null and therefore we will determine the fact with the other element...
            return true;
        }

        if (!element.isInherited()) {
            return false;
        }

        String elementSig = Util.toUniqueString(element.getModelRepresentation());
        String declSig = Util.toUniqueString(element.getDeclaringElement().asType());

        if (!Objects.equals(elementSig, declSig)) {
            return false;
        }

        javax.lang.model.element.TypeElement declaringType = findTypeOf(element.getDeclaringElement());

        JavaTypeElement declaringClass = env.getTypeMap().get(declaringType);

        return declaringClass != null && declaringClass.isInAPI();
    }

    private static class TypeAndUseSite {
        final DeclaredType type;
        final UseSite useSite;

        public TypeAndUseSite(DeclaredType type, UseSite useSite) {
            this.type = type;
            this.useSite = useSite;
        }
    }
}
