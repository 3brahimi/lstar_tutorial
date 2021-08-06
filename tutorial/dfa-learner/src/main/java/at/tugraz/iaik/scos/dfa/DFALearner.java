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

package at.tugraz.iaik.scos.dfa;

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
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.fsa.NFA;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.automata.fsa.impl.compact.CompactNFA;
import net.automatalib.serialization.dot.GraphDOT;
import net.automatalib.util.automata.builders.AutomatonBuilders;
import net.automatalib.visualization.Visualization;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;

/**
 * This example demonstrates the classical Angluin LStar for DFAs.
 * 
 * @author Masoud Ebrahimi
 */
@SuppressWarnings("PMD.SystemPrintln")
public final class DFALearner {

    private static final int EXPLORATION_DEPTH = 4;
    private Alphabet<Character> inputs;
    private DFAMembershipOracle<Character> sul;
    private DFACounterOracle<Character> mqOracle;
    private DFAWMethodEQOracle<Character> eqOracle;
    GenericObservationTable<Character, Boolean> table;
    ObservationTableASCIIWriter<Character, Boolean> tableWriter;
    DFA<?, Character> hypothesis;

    public static void main(String[] args) throws IOException {
        DFALearner experiment = new DFALearner();
        experiment.learn(constructSUL());
    }

    public void learn(CompactDFA<Character> target) throws IOException {
        // @formatter:off
        inputs      = target.getInputAlphabet();
        sul         = new DFASimulatorOracle<>(target);
        mqOracle    = new DFACounterOracle<>(sul, "Membership Queries");
        eqOracle    = new DFAWMethodEQOracle<>(mqOracle, EXPLORATION_DEPTH);
        table       = new GenericObservationTable<Character, Boolean>(inputs);
        tableWriter = new ObservationTableASCIIWriter<>(input -> input.toString(), output -> (output ? "1" : "0"), true);
        // @formatter:on

        List<Word<Character>> prefixes = initialPrefixes();
        List<Word<Character>> suffixes = initialSuffixes();
        table.initialize(prefixes, suffixes, mqOracle);
        System.out.println("=======================================================");
        System.out.println(" Initialized Observation Table");
        System.out.println("-------------------------------------------------------");
        tableWriter.write(table, System.out);
        promptEnterKey("start learning");

        Integer round = 0;
        DefaultQuery<Character, Boolean> ce;
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
            
            refineTable(ce);
        }

        System.out.println("=======================================================");
        System.out.println("Final Hypothesis:");
        System.out.println("-------------------------------------------------------");
        System.out.println(mqOracle.getStatisticalData().getSummary());
        System.out.println("Rounds: " + (++round).toString());
        System.out.println("States: " + hypothesis.size());
        System.out.println("Sigma: " + inputs.size());
        System.out.println();
        System.out.println("Observation table:");
        tableWriter.write(table, System.out);
        System.out.println();
        System.out.println("Model: ");
        GraphDOT.write(hypothesis, inputs, System.out); // may throw IOException!
    }

    protected static List<Word<Character>> initialPrefixes() {
        return Collections.singletonList(Word.epsilon());
    }

    protected static List<Word<Character>> initialSuffixes() {
        return Collections.singletonList(Word.epsilon());
    }

    protected void closeTable() throws IOException {
        if (table.isClosed())
            return;

        System.out.println("=======================================================");
        System.out.println(" Closing Observation Table");
        System.out.println("-------------------------------------------------------");

        NFA<?, Character> nfa = getPartialModel();
        System.out.println("Open hypothesis model: ");
        GraphDOT.write(nfa, inputs, System.out);
        System.out.println();
        Visualization.visualize(nfa, inputs);

        while (!table.isClosed()) {
            Row<Character> unclosed = table.findUnclosedRow();
            Word<Character> label = unclosed.getLabel();
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

        NFA<?, Character> nfa = getPartialModel();
        System.out.println("Inconsistent hypothesis model: ");
        GraphDOT.write(nfa, inputs, System.out);
        System.out.println();
        Visualization.visualize(nfa, inputs);

        Inconsistency<Character> inconsistency;
        while (!table.isConsistent()) {
            inconsistency = table.findInconsistency();
            Word<Character> suffix = findDistinguishingSuffix(inconsistency);
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

    protected void refineTable(DefaultQuery<Character, Boolean> ce) throws IOException {
        Word<Character> word = ce.getInput();
        table.addShortPrefixes(word.prefixes(false), mqOracle);

        System.out.println();
        NFA<?, Character> nfa = getPartialModel();
        System.out.println("Refined partial model: ");
        GraphDOT.write(nfa, inputs, System.out);
        System.out.println();
        System.out.println("Refined table: ");
        tableWriter.write(table, System.out);
        Visualization.visualize(nfa, inputs);
    }

    protected CompactNFA<Character> getPartialModel() {
        CompactNFA<Character> model = new CompactNFA<>(inputs);
        List<Integer> stateMap = new ArrayList<>(table.numberOfRows());

        List<Row<Character>> shortPrefixRows = table.getShortPrefixRows();
        List<Row<Character>> allRows = new ArrayList<>(table.getAllRows());

        // Creating states
        for (Row<Character> row : shortPrefixRows) {
            int state = model.addIntState(table.cellContents(row, table.getSuffixes().indexOf(Word.epsilon())));
            stateMap.add(row.getRowContentId(), state);
        }

        // Creating dummy states
        for (Row<Character> row : allRows) {
            int id = row.getRowContentId();
            if (stateMap.contains(id))
                continue;
            int state = model.addIntState(table.cellContents(row, table.getSuffixes().indexOf(Word.epsilon())));
            stateMap.add(id, state);
        }

        // Transition relation
        for (Row<Character> row : shortPrefixRows) {
            int state = stateMap.get(row.getRowContentId());
            if (row.getLabel().isEmpty()) {
                model.setInitial(state, true);
            }

            for (int i = 0; i < inputs.size(); i++) {
                Row<Character> successorRow = row.getSuccessor(i);
                int successorState = stateMap.get(successorRow.getRowContentId());
                model.addTransition(state, i, successorState);
            }
        }
        return model;
    }

    protected CompactDFA<Character> getHypothesisModel() {
        CompactDFA<Character> model = new CompactDFA<>(inputs);
        List<Integer> stateMap = new ArrayList<>(table.numberOfRows());

        List<Row<Character>> shortPrefixRows = table.getShortPrefixRows();

        // Creating states
        for (Row<Character> row : shortPrefixRows) {
            int state = model.addIntState(table.cellContents(row, table.getSuffixes().indexOf(Word.epsilon())));
            stateMap.add(row.getRowContentId(), state);
        }

        // Transition relation
        for (Row<Character> row : shortPrefixRows) {
            int state = stateMap.get(row.getRowContentId());
            if (row.getLabel().isEmpty()) {
                model.setInitial(state, true);
            }

            for (int i = 0; i < inputs.size(); i++) {
                Row<Character> successorRow = row.getSuccessor(i);
                int successorState = stateMap.get(successorRow.getRowContentId());
                Integer t = model.getTransition(state, inputs.getSymbol(i));
                if (t == null)
                    model.addTransition(state, inputs.getSymbol(i), successorState);
            }
        }
        return model;
    }

    protected Word<Character> findDistinguishingSuffix(Inconsistency<Character> inconsistency) {
        // inconsistency = 2 x red rows + 1 input symbol
        int inputIdx = inputs.getSymbolIndex(inconsistency.getSymbol());

        Row<Character> firstSuccessor = inconsistency.getFirstRow().getSuccessor(inputIdx);
        Row<Character> secondSuccessor = inconsistency.getSecondRow().getSuccessor(inputIdx);

        int numSuffixes = table.getSuffixes().size();

        for (int i = 0; i < numSuffixes; i++) {
            Boolean firstOutput = table.cellContents(firstSuccessor, i);
            Boolean secondOutput = table.cellContents(secondSuccessor, i);
            if (!firstOutput.equals(secondOutput)) {
                Character sym = inputs.getSymbol(inputIdx);
                Word<Character> suffix = table.getSuffixes().get(i);
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
    public static CompactDFA<Character> constructSUL() {
        // input alphabet contains characters 'a'..'b'
        Alphabet<Character> sigma = Alphabets.characters('a', 'b');

        // @formatter:off
        // create automaton
        return AutomatonBuilders.newDFA(sigma)
                .withInitial("q0")
                .from("q0")
                    .on('a').to("q1")
                    .on('b').to("q2")
                .from("q1")
                    .on('a').to("q0")
                    .on('b').to("q3")
                .from("q2")
                    .on('a').to("q3")
                    .on('b').to("q0")
                .from("q3")
                    .on('a').to("q2")
                    .on('b').to("q1")
                .withAccepting("q0")
                .create();
        // @formatter:on
    }
}