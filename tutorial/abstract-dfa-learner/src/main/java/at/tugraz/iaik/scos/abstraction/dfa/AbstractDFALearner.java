/* Copyright (C) 2013-2021 TU Dortmund
 * This file is part of LearnLib, http://www.learnlib.de/.
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

package at.tugraz.iaik.scos.abstraction.dfa;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.learnlib.api.oracle.MembershipOracle.DFAMembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.observationtable.Row;
import de.learnlib.datastructure.observationtable.GenericObservationTable;
import de.learnlib.datastructure.observationtable.Inconsistency;
import de.learnlib.datastructure.observationtable.writer.ObservationTableASCIIWriter;
import de.learnlib.filter.statistic.oracle.DFACounterOracle;
import de.learnlib.oracle.equivalence.DFAWMethodEQOracle;
import de.learnlib.oracle.membership.SimulatorOracle.DFASimulatorOracle;
import de.learnlib.oracle.membership.MappedOracle;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.fsa.NFA;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.automata.fsa.impl.compact.CompactNFA;
import net.automatalib.serialization.dot.GraphDOT;
import net.automatalib.visualization.Visualization;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;

/**
 * This example demonstrates the role of abstraction in automata learning.
 * 
 * @author Masoud Ebrahimi
 */
@SuppressWarnings("PMD.SystemPrintln")
public final class AbstractDFALearner {

    private static final int EXPLORATION_DEPTH = 4;
    private Alphabet<Integer> inputs;
    private List<Word<Integer>> statePrefixMap;
    private DFAMembershipOracle<Integer> sul;
    private DFACounterOracle<Integer> mqOracle;
    private DFACounterOracle<Integer> eqMqOracle;
    private DFAWMethodEQOracle<Integer> eqOracle;
    GenericObservationTable<Integer, Boolean> table;
    ObservationTableASCIIWriter<Integer, Boolean> tableWriter;
    DFA<?, Integer> hypothesis;

    public static void main(String[] args) throws IOException {
        AbstractDFALearner experiment = new AbstractDFALearner();
        experiment.learn(constructSUL(4));
    }

    public void learn(CompactDFA<Integer> target) throws IOException {
        // @formatter:off
        inputs      = Alphabets.integers(1, 12);
        sul         = new DFASimulatorOracle<>(target);
        mqOracle    = new DFACounterOracle<>(sul, "Membership Queries");
        eqMqOracle  = new DFACounterOracle<>(sul, "Membership Queries (Conformance Testing)");
        eqOracle    = new DFAWMethodEQOracle<>(eqMqOracle, EXPLORATION_DEPTH);
        table       = new GenericObservationTable<>(inputs);
        tableWriter = new ObservationTableASCIIWriter<>(input -> input.toString(), output -> (output ? "1" : "0"), true);
        // @formatter:on

        Visualization.visualize(target, inputs);

        List<Word<Integer>> prefixes = initialPrefixes();
        List<Word<Integer>> suffixes = initialSuffixes();
        table.initialize(prefixes, suffixes, mqOracle);
        System.out.println("=======================================================");
        System.out.println(" Initialized Observation Table");
        System.out.println("-------------------------------------------------------");
        tableWriter.write(table, System.out);
        promptEnterKey("start learning");

        Integer round = 0;
        DefaultQuery<Integer, Boolean> ce;
        while (true) {

            closeTable();
            if(consistentTable()) continue;
            hypothesis = getHypothesisModel();
            ce = eqOracle.findCounterExample(hypothesis, inputs);
            if (ce == null) {
                break;
            }
            
            // Print some useful information
            System.out.println("=======================================================");
            System.out.println(" Hypothesis " + (++round).toString() + ":");
            System.out.println("-------------------------------------------------------");
            tableWriter.write(table, System.out);
            System.out.println();
            System.out.println("Model: ");
            GraphDOT.write(hypothesis, inputs, System.out);
            System.out.println("Counter example: " + ce.toString());
            System.out.println();
            
            refineTableColumns(ce);
        }

        System.out.println("=======================================================");
        System.out.println("Final Hypothesis:");
        System.out.println("-------------------------------------------------------");
        System.out.println(mqOracle.getStatisticalData().getSummary());
        System.out.println(eqMqOracle.getStatisticalData().getSummary());
        System.out.println("Rounds: " + (++round).toString());
        System.out.println("States: " + hypothesis.size());
        System.out.println("Sigma: " + inputs.size());
        System.out.println();
        System.out.println("Observation table:");
        tableWriter.write(table, System.out);
        System.out.println();
        System.out.println("Model: ");
        GraphDOT.write(hypothesis, inputs, System.out); // may throw IOException!
        Visualization.visualize(hypothesis, inputs);
    }

    protected static List<Word<Integer>> initialPrefixes() {
        return Collections.singletonList(Word.epsilon());
    }

    protected static List<Word<Integer>> initialSuffixes() {
        return Collections.singletonList(Word.epsilon());
    }

    protected void closeTable() throws IOException {
        if (table.isClosed())
            return;

        System.out.println("=======================================================");
        System.out.println(" Closing Observation Table");
        System.out.println("-------------------------------------------------------");

        NFA<?, Integer> nfa = getPartialModel();
        System.out.println("Open hypothesis model: ");
        GraphDOT.write(nfa, inputs, System.out);
        System.out.println();
        Visualization.visualize(nfa, inputs);

        while (!table.isClosed()) {
            Row<Integer> unclosed = table.findUnclosedRow();
            Word<Integer> label = unclosed.getLabel();
            table.addShortPrefixes(label.prefixes(false), mqOracle);
        }

        nfa = getPartialModel();
        System.out.println("Closed hypothesis model: ");
        GraphDOT.write(nfa, inputs, System.out);
        System.out.println();

        System.out.println("Closed table: ");
        tableWriter.write(table, System.out);
        Visualization.visualize(nfa, inputs);
    }

    protected boolean consistentTable() throws IOException {
        if (table.isConsistent())
            return false;

        System.out.println("=======================================================");
        System.out.println(" Consistent Observation Table");
        System.out.println("-------------------------------------------------------");

        NFA<?, Integer> nfa = getPartialModel();
        System.out.println("Inconsistent hypothesis model: ");
        GraphDOT.write(nfa, inputs, System.out);
        System.out.println();
        Visualization.visualize(nfa, inputs);

        Inconsistency<Integer> inconsistency;
        while (!table.isConsistent()) {
            inconsistency = table.findInconsistency();
            Word<Integer> suffix = findDistinguishingSuffix(inconsistency);
            table.addSuffixes(suffix.suffixes(false), mqOracle);
        }

        nfa = getPartialModel();
        System.out.println("Consistent hypothesis model: ");
        GraphDOT.write(nfa, inputs, System.out);
        System.out.println();

        System.out.println("Consistent table: ");
        tableWriter.write(table, System.out);
        Visualization.visualize(nfa, inputs);
        return true;
    }

    protected void refineTableColumns(DefaultQuery<Integer, Boolean> ce) throws IOException {
        promptEnterKey("refine the hypothesis");
        Word<Integer> word = ce.getInput();

        Integer state = 0;

        Word<Integer> query;
        Word<Integer> suffix = Word.epsilon();

        Boolean lastOutputSymbol;
        Boolean prevLastOutputSymbol = ce.getOutput();
        for (Word<Integer> prefix : word.prefixes(false)) {
            suffix = word.suffix(word.length() - prefix.length());

            state = (Integer) hypothesis.getState(prefix);
            query = statePrefixMap.get(state).concat(suffix);

            lastOutputSymbol = eqMqOracle.answerQuery(query);
            if( !prevLastOutputSymbol.equals(lastOutputSymbol) ) {
                break;
            }
        }

        if (suffix.length()>0) {
            List<Word<Integer>> suffixes = new ArrayList<>(suffix.suffixes(false));
            suffixes.remove(Word.epsilon());
            table.addSuffixes(suffixes, mqOracle);
        }

        System.out.println("Refined table: ");
        tableWriter.write(table, System.out);
    }

    protected CompactNFA<Integer> getPartialModel() {
        CompactNFA<Integer> model = new CompactNFA<>(inputs);
        List<Integer> stateMap = new ArrayList<>(table.numberOfRows());

        List<Row<Integer>> shortPrefixRows = table.getShortPrefixRows();
        List<Row<Integer>> allRows = new ArrayList<>(table.getAllRows());

        // Creating states
        for (Row<Integer> row : shortPrefixRows) {
            int state = model.addIntState(table.cellContents(row, table.getSuffixes().indexOf(Word.epsilon())));
            stateMap.add(row.getRowContentId(), state);
        }

        // Creating dummy states
        for (Row<Integer> row : allRows) {
            int id = row.getRowContentId();
            if (stateMap.contains(id))
                continue;
            int state = model.addIntState(table.cellContents(row, table.getSuffixes().indexOf(Word.epsilon())));
            stateMap.add(id, state);
        }

        // Transition relation
        for (Row<Integer> row : shortPrefixRows) {
            int state = stateMap.get(row.getRowContentId());
            if (row.getLabel().isEmpty()) {
                model.setInitial(state, true);
            }

            for (int i = 0; i < inputs.size(); i++) {
                Row<Integer> successorRow = row.getSuccessor(i);
                int successorState = stateMap.get(successorRow.getRowContentId());
                model.addTransition(state, i, successorState);
            }
        }
        return model;
    }

    protected CompactDFA<Integer> getHypothesisModel() {
        CompactDFA<Integer> model = new CompactDFA<>(inputs);
        List<Integer> stateMap = new ArrayList<>(table.numberOfRows());
        statePrefixMap = new ArrayList<>(table.numberOfRows());

        List<Row<Integer>> shortPrefixRows = table.getShortPrefixRows();

        // Creating states
        for (Row<Integer> row : shortPrefixRows) {
            int state = model.addIntState(table.cellContents(row, table.getSuffixes().indexOf(Word.epsilon())));
            stateMap.add(row.getRowContentId(), state);
            statePrefixMap.add(state, row.getLabel());
        }

        // Transition relation
        for (Row<Integer> row : shortPrefixRows) {
            int state = stateMap.get(row.getRowContentId());
            if (row.getLabel().isEmpty()) {
                model.setInitial(state, true);
            }

            for (int i = 0; i < inputs.size(); i++) {
                Row<Integer> successorRow = row.getSuccessor(i);
                int successorState = stateMap.get(successorRow.getRowContentId());
                Integer t = model.getTransition(state, inputs.getSymbol(i));
                if (t == null)
                    model.addTransition(state, inputs.getSymbol(i), successorState);
            }
        }
        return model;
    }

    protected Word<Integer> findDistinguishingSuffix(Inconsistency<Integer> inconsistency) {
        // inconsistency = 2 x red rows + 1 input symbol
        int inputIdx = inputs.getSymbolIndex(inconsistency.getSymbol());

        Row<Integer> firstSuccessor = inconsistency.getFirstRow().getSuccessor(inputIdx);
        Row<Integer> secondSuccessor = inconsistency.getSecondRow().getSuccessor(inputIdx);

        int numSuffixes = table.getSuffixes().size();

        for (int i = 0; i < numSuffixes; i++) {
            Boolean firstOutput = table.cellContents(firstSuccessor, i);
            Boolean secondOutput = table.cellContents(secondSuccessor, i);
            if (!firstOutput.equals(secondOutput)) {
                Integer sym = inputs.getSymbol(inputIdx);
                Word<Integer> suffix = table.getSuffixes().get(i);
                return suffix.prepend(sym);
            }
        }
        throw new IllegalArgumentException("Bogus inconsistency");
    }

    public void promptEnterKey(String prompt) {
        System.out.println("Press \"ENTER\" to continue to " + prompt + "...");
        try {
            System.in.read();
        } catch (Exception e) {
            System.out.println(e.toString());
        }
    }

    /**
     * creates example from Angluin's seminal paper.
     *
     * @return example dfa
     */
    public static CompactDFA<Integer> constructSUL(int n) {
        // input alphabet contains Integers 'a'..'b'
        Alphabet<Integer> sigma = Alphabets.integers(1, 1000);
        
        // create automaton
        CompactDFA<Integer> dfa = new CompactDFA<>(sigma);
        
        dfa.addInitialState(false);
        for(int i=1; i < n - 1; i++)
            dfa.addState(i%2==1);
        dfa.addState(n%2==0);
        
        for (Integer state: dfa.getStates())
            for( Integer in : sigma )
                dfa.addTransition(state, in, (in + state) % n);
        return dfa;
    }
}