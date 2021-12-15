/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.regex.tregex.nfa;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.result.PreCalculatedResultFactory;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.parser.Counter;
import com.oracle.truffle.regex.tregex.parser.ast.GroupBoundaries;
import com.oracle.truffle.regex.tregex.string.Encodings.Encoding;

/**
 * Used for pre-calculating and finding the result of tree-like regular expressions. A regular
 * expression is tree-like if it does not contain any loops (* or +).
 */
public final class NFATraceFinderGenerator {

    /**
     * NFA we shall compute a reverse tree-shaped NFA from.
     */
    private final NFA originalNFA;

    /**
     * States of the new NFA generated by this class.
     */
    private final List<NFAState> states;

    /**
     * All states in the new NFA are copies of states of the original NFA. This map maps all
     * original states to their respective copies, in the order the copies were created, so that
     * {@code duplicatedStatesMap[originalState.getId()]} will result in a list of copies of the
     * original state, where the first entry is the first copy that was created.
     */
    private final List<NFAState>[] duplicatedStatesMap;

    /**
     * Every path through the original NFA will correspond to a path from a leaf node of the new
     * tree-shaped NFA to its root. The result of one such path corresponds to one entry in this
     * list, and the order of this list corresponds to the result's priorities -
     * {@code resultList.get(0)} has higher priority than {@code resultList.get(1)} and so on.
     */
    private final List<PreCalculatedResultFactory> resultList = new ArrayList<>();

    /**
     * Equal results are consolidated using this map.
     */
    private final EconomicMap<PreCalculatedResultFactory, PreCalculatedResultFactory> resultDeDuplicationMap = EconomicMap.create();

    private final boolean trackLastGroup;

    private final Counter.ThresholdCounter stateID = new Counter.ThresholdCounter(TRegexOptions.TRegexMaxNFASize, "TraceFinder NFA explosion");
    private final Counter.ThresholdCounter transitionID = new Counter.ThresholdCounter(TRegexOptions.TRegexMaxNFASize, "TraceFinder NFA transition explosion");

    @SuppressWarnings("unchecked")
    private NFATraceFinderGenerator(NFA originalNFA) {
        this.originalNFA = originalNFA;
        this.states = new ArrayList<>(originalNFA.getStates().length * 2);
        this.duplicatedStatesMap = new ArrayList[originalNFA.getStates().length];
        this.trackLastGroup = originalNFA.getAst().getOptions().getFlavor().usesLastGroupResultField();
    }

    /**
     * Generates a NFA that can be used to generate a backward-searching DFA that can find the
     * result (capture group offsets) of a regex match found by a forward-searching DFA.
     * <p>
     * The idea behind this is the following: If a regular expression does not contain any loops (+
     * and *), its NFA will be a directed acyclic graph, which can be converted to a tree. If the
     * search pattern is a tree, we can find all possible results (capture group offsets) ahead of
     * time, by a simple tree traversal. Knowing all possible results in advance saves us the work
     * of updating individual result indices during the search. The only problem is that we
     * generally have to <em>find</em> the pattern in a string - the result will not necessarily
     * start at the index we start searching from.
     * <p>
     * Doing an un-anchored search with a DFA requires that we add a loop in the beginning. For
     * example, in order to find {@code /a(b|cd)/} in {@code "____acd____"}, we have to prepend the
     * expression with {@code [\u0000-\uffff]*}, and the resulting NFA can no longer be seen as a
     * tree. In order to still gain some performance from the ability to pre-calculate all results,
     * we can do the following:
     * <ul>
     * <li>We create a "reverse" tree from the NFA, so the root of the tree is the final state of
     * the original NFA. We annotate every node in the tree-shaped NFA with all pre-calculated
     * results that are reachable from the respective node. All leaves of the tree have exactly one
     * possible pre-calculated result.</li>
     * <li>We find the regex match using a regular forward searching DFA, with no regard to capture
     * groups.</li>
     * <li>The result of the forward search gives us the anchor necessary to do a search based on
     * the tree-shaped NFA, and it also gives us the information that a match has definitely been
     * found.</li>
     * <li>We can now use this information to search for the pre-calculated result that corresponds
     * to the string we found. To do so, we create a DFA from the tree-shaped NFA, where all states
     * whose NFA state set contains only one possible pre-calculated result are final states without
     * further successors.</li>
     * <li>To find the correct pre-calculated result, we simply run the new DFA in reverse
     * direction, starting from the index we found with the forward searching DFA.</li>
     * </ul>
     *
     * <pre>
     *     {@code
     *     Example:
     *     regular expression: /a(b|c)d(e|fg)/
     *
     *     this expression has two possible results:
     *     (in the form: [start CG 0, end CG 0, start CG 1, end CG 1,... ])
     *     0: [0, 4, 1, 2, 3, 4]
     *     1: [0, 5, 1, 2, 3, 5]
     *
     *     NFA in reverse tree form:
     *          I
     *         /  \
     *         e  g
     *         |  |
     *         d  f
     *       / |  |
     *      b  c  d
     *      |  |  | \
     *      a  a  b  c
     *            |  |
     *            a  a
     *
     *     When searching for the correct result, we can immediately determine it based on the last character:
     *     e -> result 0
     *     g -> result 1
     *     }
     * </pre>
     *
     * We also have to take care of the order in which results are to be found. For example, in the
     * expression {@code /a(b)c|ab(c)/}, we always have to return the result created from taking the
     * first branch, never the second. Therefore, we create the reverse tree-shaped NFA while
     * traversing the original NFA in priority order.
     *
     * @param nfa the NFA used for the forward-searching DFA.
     * @return a new NFA suitable to find the result while searching backward.
     */
    public static NFA generateTraceFinder(NFA nfa) {
        return new NFATraceFinderGenerator(nfa).run();
    }

    private static final class PathElement {

        private final NFAStateTransition transition;
        private int i = 0;

        private PathElement(NFAStateTransition transition) {
            this.transition = transition;
        }

        public NFAStateTransition getTransition() {
            return transition;
        }

        public boolean hasNextTransition() {
            return i < transition.getTarget().getSuccessors().length;
        }

        public NFAStateTransition getNextTransition() {
            return transition.getTarget().getSuccessors()[i++];
        }
    }

    private NFA run() {
        NFAState dummyInitialState = copy(originalNFA.getDummyInitialState());
        NFAStateTransition newAnchoredEntry = copyEntry(dummyInitialState, originalNFA.getReverseAnchoredEntry());
        NFAStateTransition newUnAnchoredEntry = copyEntry(dummyInitialState, originalNFA.getReverseUnAnchoredEntry());
        dummyInitialState.setPredecessors(new NFAStateTransition[]{newAnchoredEntry, newUnAnchoredEntry});
        ArrayList<PathElement> graphPath = new ArrayList<>();
        for (NFAStateTransition entry : new NFAStateTransition[]{originalNFA.getAnchoredEntry()[0], originalNFA.getUnAnchoredEntry()[0]}) {
            for (NFAStateTransition t : entry.getTarget().getSuccessors()) {
                // All paths start from the original initial states, which will be duplicated and
                // become leaf nodes in the tree.
                PathElement curElement = new PathElement(t);
                while (true) {
                    // The graph-path contains nodes that have not been converted to tree form
                    // yet, and must be treated differently than the rest of the path.
                    while (duplicatedStatesMap[curElement.getTransition().getTarget().getId()] == null) {
                        graphPath.add(curElement);
                        curElement = new PathElement(curElement.getNextTransition());
                    }
                    /*
                     * We hit a node that has been converted to tree form already, so from here all
                     * nodes will have exactly one parent node (getNext().size() == 1). To create a
                     * proper tree, we have to duplicate the graphPath for all duplicates of the
                     * node we hit. Initially, we will hit one of newUnAnchoredFinalState and
                     * newAnchoredFinalState here.
                     */
                    for (NFAState duplicate : duplicatedStatesMap[curElement.getTransition().getTarget().getId()]) {
                        int resultID = resultList.size();
                        if (resultID == TRegexOptions.TRegexTraceFinderMaxNumberOfResults) {
                            throw new UnsupportedRegexException("TraceFinder: too many possible results");
                        }
                        NFAState lastCopied = copy(entry.getTarget(), resultID);
                        PreCalculatedResultFactory result = resultFactory();
                        // create a copy of the graph path
                        int iResult = 0;
                        for (int i = 0; i < graphPath.size(); i++) {
                            final NFAStateTransition pathTransition = graphPath.get(i).getTransition();
                            NFAState copy = copy(pathTransition.getTarget(), resultID);
                            createTransition(lastCopied, copy, pathTransition, result, iResult);
                            iResult += getEncodedSize(copy);
                            lastCopied = copy;
                        }
                        // link the copied path to the existing tree
                        createTransition(lastCopied, duplicate, curElement.getTransition(), result, iResult);
                        // traverse the existing tree to the root to complete the pre-calculated
                        // result.
                        NFAState treeNode = duplicate;
                        while (!treeNode.isFinalState()) {
                            iResult += getEncodedSize(treeNode);
                            assert treeNode.getSuccessors().length == 1;
                            treeNode.addPossibleResult(resultID);
                            GroupBoundaries groupBoundaries = treeNode.getSuccessors()[0].getGroupBoundaries();
                            groupBoundaries.applyToResultFactory(result, iResult, trackLastGroup);
                            treeNode = treeNode.getSuccessors()[0].getTarget();
                        }
                        treeNode.addPossibleResult(resultID);
                        result.setLength(iResult);
                        PreCalculatedResultFactory existingResult = resultDeDuplicationMap.get(result);
                        if (existingResult == null) {
                            resultDeDuplicationMap.put(result, result);
                        } else {
                            result = existingResult;
                        }
                        resultList.add(result);
                        assert resultList.get(resultID) == result;
                    }
                    // We processed one full path. Now, we have to explore all branches in the
                    // graph-path, depth-first.
                    while (!graphPath.isEmpty() && !graphPath.get(graphPath.size() - 1).hasNextTransition()) {
                        graphPath.remove(graphPath.size() - 1);
                    }
                    if (graphPath.isEmpty()) {
                        break;
                    } else {
                        curElement = new PathElement(graphPath.get(graphPath.size() - 1).getNextTransition());
                    }
                }
            }
        }
        PreCalculatedResultFactory[] preCalculatedResults;
        if (resultDeDuplicationMap.size() == 1) {
            preCalculatedResults = new PreCalculatedResultFactory[]{resultList.get(0)};
        } else {
            preCalculatedResults = resultList.toArray(new PreCalculatedResultFactory[0]);
        }
        for (NFAState s : states) {
            s.linkPredecessors();
        }
        return new NFA(originalNFA.getAst(), dummyInitialState, null, null, newAnchoredEntry, newUnAnchoredEntry, states, stateID, transitionID, preCalculatedResults);
    }

    private NFAStateTransition createTransition(NFAState source, NFAState target, NFAStateTransition originalTransition,
                    PreCalculatedResultFactory preCalcResult, int preCalcResultIndex) {
        originalTransition.getGroupBoundaries().applyToResultFactory(preCalcResult, preCalcResultIndex, trackLastGroup);
        NFAStateTransition copy = new NFAStateTransition((short) transitionID.inc(), source, target, originalTransition.getCodePointSet(), originalTransition.getGroupBoundaries());
        source.setSuccessors(new NFAStateTransition[]{copy}, true);
        return copy;
    }

    private PreCalculatedResultFactory resultFactory() {
        return new PreCalculatedResultFactory(originalNFA.getAst().getNumberOfCaptureGroups(), originalNFA.getAst().getOptions().getFlavor().usesLastGroupResultField());
    }

    private NFAStateTransition copyEntry(NFAState dummyInitialState, NFAStateTransition originalReverseEntry) {
        return new NFAStateTransition((short) transitionID.inc(), copy(originalReverseEntry.getSource()), dummyInitialState, originalReverseEntry.getCodePointSet(),
                        GroupBoundaries.getEmptyInstance(originalNFA.getAst().getLanguage()));
    }

    private NFAState copy(NFAState s) {
        final NFAState copy = s.createTraceFinderCopy((short) stateID.inc());
        registerCopy(s, copy);
        return copy;
    }

    private NFAState copy(NFAState s, int resultID) {
        final NFAState copy = copy(s);
        copy.addPossibleResult(resultID);
        return copy;
    }

    private void registerCopy(NFAState original, NFAState copy) {
        if (duplicatedStatesMap[original.getId()] == null) {
            duplicatedStatesMap[original.getId()] = new ArrayList<>();
        }
        duplicatedStatesMap[original.getId()].add(copy);
        states.add(copy);
        assert states.get(copy.getId()) == copy;
    }

    private int getEncodedSize(NFAState s) {
        Encoding encoding = originalNFA.getAst().getEncoding();
        assert encoding.isFixedCodePointWidth(s.getCharSet());
        return encoding.getEncodedSize(s.getCharSet().getMin());
    }
}
