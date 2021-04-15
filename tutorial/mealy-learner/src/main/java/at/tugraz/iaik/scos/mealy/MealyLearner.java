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

package at.tugraz.iaik.scos.mealy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.learnlib.api.oracle.MembershipOracle.MealyMembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.observationtable.Row;
import de.learnlib.datastructure.observationtable.GenericObservationTable;
import de.learnlib.datastructure.observationtable.Inconsistency;
import de.learnlib.datastructure.observationtable.writer.ObservationTableASCIIWriter;
import de.learnlib.filter.statistic.oracle.MealyCounterOracle;
import de.learnlib.oracle.equivalence.MealyWMethodEQOracle;
import de.learnlib.oracle.membership.SimulatorOracle.MealySimulatorOracle;
import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.automata.transducers.impl.compact.CompactMealy;
import net.automatalib.automata.transducers.impl.compact.CompactMealyTransition;
import net.automatalib.serialization.dot.GraphDOT;
import net.automatalib.util.automata.builders.AutomatonBuilders;
import net.automatalib.visualization.Visualization;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;

/**
 * This is an example of classical LStar_M algorithm to learn Mealy Machines.
 * 
 * @author Masoud Ebrahimi
 */
@SuppressWarnings("PMD.SystemPrintln")
public final class MealyLearner {

    private static final int EXPLORATION_DEPTH = 4;
    private List<Word<Character>> statePrefixMap;
    private Alphabet<Character> inputs;
    private MealyMembershipOracle<Character, Character> sul;
    private MealyCounterOracle<Character, Character> mqOracle;
    private MealyWMethodEQOracle<Character, Character> eqOracle;
    GenericObservationTable<Character, Word<Character>> table;
    ObservationTableASCIIWriter<Character, Word<Character>> tableWriter;
    MealyMachine<Integer, Character, CompactMealyTransition<Character>, Character> hypothesis;

    public static void main(String[] args) throws IOException {
        MealyLearner experiment = new MealyLearner();
        experiment.learn(constructSUL());
    }

    protected void learn(CompactMealy<Character, Character> target) throws IOException {
        System.out.println("Learning the following Moore machine...");
        GraphDOT.write(target, target.getInputAlphabet(), System.out);

        // @formatter:off
        inputs      = target.getInputAlphabet();
        sul         = new MealySimulatorOracle<>(target);
        mqOracle    = new MealyCounterOracle<>(sul, "Output Queries");
        eqOracle    = new MealyWMethodEQOracle<>(mqOracle, EXPLORATION_DEPTH);
        table       = new GenericObservationTable<>(inputs);
        tableWriter = new ObservationTableASCIIWriter<>();
        // @formatter:on

        System.out.println("=======================================================");
        System.out.println(" Empty Observation Table");
        System.out.println("-------------------------------------------------------");
        tableWriter.write(table, System.out);
        promptEnterKey("initialize the table");

        List<Word<Character>> prefixes = initialPrefixes();
        List<Word<Character>> suffixes = initialSuffixes(inputs);
        table.initialize(prefixes, suffixes, mqOracle);
        System.out.println("=======================================================");
        System.out.println(" Initialized Observation Table");
        System.out.println("-------------------------------------------------------");
        tableWriter.write(table, System.out);
        promptEnterKey("start learning");

        Integer round = 0;
        DefaultQuery<Character, Word<Character>> ce;
        while (true) {

            closeTable();
            consistentTable();
            hypothesis = getHypothesisModel();

            System.out.println("=======================================================");
            System.out.println(" Hypothesis " + (++round).toString() + ":");
            System.out.println("-------------------------------------------------------");
            tableWriter.write(table, System.out);
            System.out.println();
            System.out.println("Model: ");
            GraphDOT.write(hypothesis, inputs, System.out);
            promptEnterKey("test the hypothesis");

            ce = eqOracle.findCounterExample(hypothesis, inputs);
            if (ce == null)
                break;
            System.out.println("Counter example: " + ce.toString());
            System.out.println();

            refineTableColumns(ce);
        }

        System.out.println("=======================================================");
        System.out.println("Final Hypothesis:");
        System.out.println("-------------------------------------------------------");
        System.out.println(mqOracle.getStatisticalData().getSummary());
        System.out.println("Table size: " + table.numberOfRows() * table.numberOfSuffixes());
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

    protected static List<Word<Character>> initialPrefixes() {
        return Collections.singletonList(Word.epsilon());
    }

    protected static List<Word<Character>> initialSuffixes(Alphabet<Character> inputs) {
        List<Word<Character>> suffixes = new ArrayList<>();
        for (Character c : inputs) {
            suffixes.add(Word.fromSymbols(c));
        }
        return suffixes;
    }

    protected void closeTable() throws IOException {
        if (table.isClosed())
            return;

        promptEnterKey("close the table");
        System.out.println("=======================================================");
        System.out.println(" Closing Observation Table");
        System.out.println("-------------------------------------------------------");

        CompactMealy<Character, Character> model = getPartialModel();
        System.out.println("Open hypothesis model: ");
        GraphDOT.write(model, inputs, System.out);
        System.out.println();

        while (!table.isClosed()) {
            Row<Character> unclosed = table.findUnclosedRow();
            Word<Character> prefix = unclosed.getLabel();
            table.addShortPrefixes(prefix.prefixes(false), mqOracle.asOracle());
        }

        model = getPartialModel();
        System.out.println("Closed hypothesis model: ");
        GraphDOT.write(model, inputs, System.out);
        System.out.println();

        System.out.println("Closed table: ");
        tableWriter.write(table, System.out);
    }

    protected void consistentTable() throws IOException {
        if (table.isConsistent())
            return;

        promptEnterKey("make the table consistent");
        System.out.println("=======================================================");
        System.out.println(" Consistent Observation Table");
        System.out.println("-------------------------------------------------------");

        Inconsistency<Character> inconsistency;
        while (!table.isConsistent()) {
            inconsistency = table.findInconsistency();
            Word<Character> suffix = findDistinguishingSuffix(inconsistency);
            if (suffix != null) {
                List<Word<Character>> suffixes = new ArrayList<>(suffix.suffixes(false));
                suffixes.remove(Word.epsilon());
                table.addSuffixes(suffixes, mqOracle);
            }
        }

        System.out.println("Consistent table: ");
        tableWriter.write(table, System.out);
    }

    protected void refineTableShortPrefixes(DefaultQuery<Character, Word<Character>> ce) throws IOException {
        promptEnterKey("refine the hypothesis");
        Word<Character> prefix = ce.getPrefix().concat(ce.getSuffix());
        table.addShortPrefixes(prefix.prefixes(false), mqOracle);

        System.out.println("Refined table: ");
        tableWriter.write(table, System.out);
    }

    protected void refineTableColumns(DefaultQuery<Character, Word<Character>> ce) throws IOException {
        promptEnterKey("refine the hypothesis");
        Word<Character> word = ce.getInput();

        int state = 0;

        Word<Character> query;
        Word<Character> output;
        Word<Character> suffix = Word.epsilon();

        Character lastOutputSymbol;
        Character prevLastOutputSymbol = ce.getOutput().lastSymbol();
        for (Word<Character> prefix : word.prefixes(false)) {
            suffix = word.suffix(word.length() - prefix.length());

            state = hypothesis.getState(prefix);
            query = statePrefixMap.get(state).concat(suffix);

            output = mqOracle.answerQuery(query);
            lastOutputSymbol = output.lastSymbol();
            if( !prevLastOutputSymbol.equals(lastOutputSymbol) ) {
                break;
            }
        }

        if (suffix.length()>0) {
        List<Word<Character>> suffixes = new ArrayList<>(suffix.suffixes(false));
        suffixes.remove(Word.epsilon());
        table.addSuffixes(suffixes, mqOracle);
        } else {
            refineTableShortPrefixes(ce);
        }

        System.out.println("Refined table: ");
        tableWriter.write(table, System.out);
    }

    protected CompactMealy<Character, Character> getPartialModel() {
        CompactMealy<Character, Character> hypothesis = new CompactMealy<>(inputs);
        List<Integer> stateMap = new ArrayList<>(table.numberOfRows());

        List<Row<Character>> shortPrefixRows = table.getShortPrefixRows();
        List<Row<Character>> allRows = new ArrayList<>(table.getAllRows());

        // Creating states
        for (Row<Character> row : shortPrefixRows) {
            int state = hypothesis.addIntState();
            stateMap.add(row.getRowContentId(), state);
        }

        // Creating dummy states
        for (Row<Character> row : allRows) {
            int id = row.getRowContentId();
            if (stateMap.contains(id))
                continue;
            int state = hypothesis.addIntState();
            stateMap.add(id, state);
        }

        // Transition relation
        for (Row<Character> row : shortPrefixRows) {
            int state = stateMap.get(row.getRowContentId());
            if (row.getLabel().isEmpty()) {
                hypothesis.setInitial(state, true);
            }

            for (int i = 0; i < inputs.size(); i++) {
                Row<Character> successorRow = row.getSuccessor(i);
                int successorState = stateMap.get(successorRow.getRowContentId());
                Word<Character> output = table.cellContents(row,
                        table.getSuffixes().indexOf(Word.fromSymbols(inputs.getSymbol(i))));
                CompactMealyTransition<Character> t = hypothesis.getTransition(state, inputs.getSymbol(i));
                if (t == null)
                    hypothesis.addTransition(state, inputs.getSymbol(i), successorState, output.firstSymbol());
            }
        }
        return hypothesis;
    }

    protected CompactMealy<Character, Character> getHypothesisModel() {
        promptEnterKey("extract the hypothesis");
        CompactMealy<Character, Character> hypothesis = new CompactMealy<>(inputs);
        List<Integer> stateMap = new ArrayList<>(table.numberOfRows());
        statePrefixMap = new ArrayList<>(table.numberOfRows());

        List<Row<Character>> shortPrefixRows = table.getShortPrefixRows();

        // Creating states
        for (Row<Character> row : shortPrefixRows) {
            if (stateMap.contains(row.getRowContentId()))
                continue;
            int state = hypothesis.addIntState();
            stateMap.add(row.getRowContentId(), state);
            statePrefixMap.add(state, row.getLabel());
        }

        // Transition relation
        for (Row<Character> row : shortPrefixRows) {
            int state = stateMap.get(row.getRowContentId());
            if (row.getLabel().isEmpty()) {
                hypothesis.setInitial(state, true);
            }

            for (int i = 0; i < inputs.size(); i++) {
                Row<Character> successorRow = row.getSuccessor(i);
                int successorState = stateMap.get(successorRow.getRowContentId());
                Word<Character> output = table.cellContents(row,
                        table.getSuffixes().indexOf(Word.fromSymbols(inputs.getSymbol(i))));
                CompactMealyTransition<Character> t = hypothesis.getTransition(state, inputs.getSymbol(i));
                if (t == null)
                    hypothesis.addTransition(state, inputs.getSymbol(i), successorState, output.firstSymbol());
            }
        }
        return hypothesis;
    }

    protected Word<Character> findDistinguishingSuffix(Inconsistency<Character> inconsistency) {
        int inputIdx = inputs.getSymbolIndex(inconsistency.getSymbol());

        Row<Character> firstSuccessor = inconsistency.getFirstRow().getSuccessor(inputIdx);
        Row<Character> secondSuccessor = inconsistency.getSecondRow().getSuccessor(inputIdx);

        int numSuffixes = table.getSuffixes().size();

        for (int i = 0; i < numSuffixes; i++) {
            Object firstOutput = table.cellContents(firstSuccessor, i);
            Object secondOutput = table.cellContents(secondSuccessor, i);
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
     * @return example moore
     */
    private static CompactMealy<Character, Character> constructSUL() {
        // input alphabet contains characters 'a'..'b'
        Alphabet<Character> inputs = Alphabets.characters('a', 'c');
        
        // @formatter:off
        // create automaton
        CompactMealy<Character, Object> mealy = 
            AutomatonBuilders.newMealy(inputs)
                .withInitial("q0")
                .from("q0")
                    .on('a').withOutput('x').to("q1")
                    .on('b').withOutput('x').to("q2")
                    .on('c').withOutput('x').to("q0")
                .from("q1")
                    .on('a').withOutput('y').to("q1")
                    .on('b').withOutput('y').to("q2")
                    .on('c').withOutput('y').to("q0")
                .from("q2")
                    .on('a').withOutput('z').to("q0")
                    .on('b').withOutput('z').to("q1")
                    .on('c').withOutput('z').to("q3")
                .from("q3")
                    .on('a').withOutput('x').to("q0")
                    .on('b').withOutput('x').to("q1")
                    .on('c').withOutput('x').to("q2")
                .create();
        // @formatter:on

        CompactMealy<Character, Character> result = new CompactMealy<>(inputs);

        for (int i = 0; i < mealy.getStates().size(); i++) {
            result.addState();
        }

        for (int state : mealy.getStates()) {
            for (Character input : inputs) {
                Integer successor = mealy.getSuccessor(state, input);
                Character output = (Character) mealy.getOutput(state, input);
                if (successor != null)
                    result.addTransition(state, input, successor, output);
            }
        }

        result.setInitialState(mealy.getInitialState());
        return result;
    }
}