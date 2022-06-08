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
package org.revapi;

import static java.util.Arrays.asList;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;

/**
 * A tree filter is something that is called repeatedly by the caller as the caller walks a tree of elements in a depth
 * first search manner. The tree filter gives the caller filtering results and walk instructions.
 * <p>
 * As a caller of some implementation of this interface, please study the documentation of the individual methods on
 * this interface to learn at what times the methods are supposed to be called.
 */
public interface TreeFilter<E extends Element<E>> {

    static <E extends Element<E>> TreeFilter<E> matchAndDescend() {
        return new TreeFilter<E>() {
            @Override
            public FilterStartResult start(E element) {
                return FilterStartResult.matchAndDescend();
            }

            @Override
            public FilterFinishResult finish(E element) {
                return FilterFinishResult.matches();
            }

            @Override
            public Map<E, FilterFinishResult> finish() {
                return Collections.emptyMap();
            }
        };
    }

    /**
     * @see #merge(BinaryOperator, BinaryOperator, FilterStartResult, FilterFinishResult, List)
     */
    @SafeVarargs
    static <E extends Element<E>> TreeFilter<E> merge(BinaryOperator<FilterStartResult> mergeStarts,
            BinaryOperator<FilterFinishResult> mergeFinishes, FilterStartResult defaultStartResult,
            FilterFinishResult defaultFinishResult, TreeFilter<E>... fs) {
        return merge(mergeStarts, mergeFinishes, defaultStartResult, defaultFinishResult, asList(fs));
    }

    /**
     * Merges the filters together using the provided functions to combine the individual results.
     * 
     * @param mergeStarts
     *            the function to combine two filter start results
     * @param mergeFinishes
     *            the function to combine two filter finish results
     * @param defaultStartResult
     *            the default start result in case there are no filters in the provided list
     * @param defaultFinishResult
     *            the default finish result in case there are no filters in the provided list
     * @param fs
     *            the list of filters to merge
     */
    static <E extends Element<E>> TreeFilter<E> merge(BinaryOperator<FilterStartResult> mergeStarts,
            BinaryOperator<FilterFinishResult> mergeFinishes, FilterStartResult defaultStartResult,
            FilterFinishResult defaultFinishResult, List<TreeFilter<E>> fs) {
        return new TreeFilter<E>() {
            @Override
            public FilterStartResult start(E element) {
                return fs.stream().map(f -> f.start(element)).reduce(mergeStarts).orElse(defaultStartResult);
            }

            @Override
            public FilterFinishResult finish(E element) {
                return fs.stream().map(f -> f.finish(element)).reduce(mergeFinishes).orElse(defaultFinishResult);
            }

            @Override
            public Map<E, FilterFinishResult> finish() {
                return fs.stream().map(TreeFilter::finish).reduce(new HashMap<>(), (ret, res) -> {
                    ret.putAll(res);
                    return ret;
                });
            }
        };
    }

    /**
     * @see #intersection(List)
     */
    @SafeVarargs
    static <E extends Element<E>> TreeFilter<E> intersection(TreeFilter<E>... fs) {
        return intersection(asList(fs));
    }

    /**
     * Returns a filter that matches if all of the provided filters match.
     */
    static <E extends Element<E>> TreeFilter<E> intersection(List<TreeFilter<E>> fs) {
        return merge(FilterStartResult::and, FilterFinishResult::and, FilterStartResult.defaultResult(),
                FilterFinishResult.defaultResult(), fs);
    }

    /**
     * @see #union(List)
     */
    @SafeVarargs
    static <E extends Element<E>> TreeFilter<E> union(TreeFilter<E>... fs) {
        return union(asList(fs));
    }

    /**
     * @return a filter that matches if at least one of the provided filters matches.
     */
    static <E extends Element<E>> TreeFilter<E> union(List<TreeFilter<E>> fs) {
        return merge(FilterStartResult::or, FilterFinishResult::or, FilterStartResult.defaultResult(),
                FilterFinishResult.defaultResult(), fs);
    }

    /**
     * This method is called when an element is about to be filtered. After this call all the children will be processed
     * (if the result instructs the caller to do so). Only after that, the {@link #finish(Element)} will be called with
     * the same element as this method.
     *
     * @param element
     *            the element to start filtering
     * 
     * @return a filter result informing the caller what was the result of filtering and whether to descend to children
     *         or not
     */
    FilterStartResult start(E element);

    /**
     * This method is called after the filtering has {@link #start(Element) started} and all children have been
     * processed by this filter.
     * <p>
     * Note that the result can still be {@link Ternary#UNDECIDED}. It is expected that such elements will in the end be
     * resolved with the {@link #finish()} method.
     *
     * @param element
     *            the element for which the filtering has finished
     * 
     * @return the result of filtering
     */
    FilterFinishResult finish(E element);

    /**
     * Called after all elements have been processed to see if any of them have changed in their filtering result (which
     * could be the case if there are dependencies between elements other than that of parent-child).
     * <p>
     * Note that the result can remain {@link Ternary#UNDECIDED}. It is upon the caller to then decide what to do with
     * such elements.
     *
     * @return the final results for elements that were previously undecided if their filtering status changed
     */
    Map<E, FilterFinishResult> finish();
}
