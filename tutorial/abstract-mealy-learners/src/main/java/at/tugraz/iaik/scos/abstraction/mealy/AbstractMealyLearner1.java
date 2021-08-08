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

package at.tugraz.iaik.scos.abstraction.mealy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import de.learnlib.api.SUL;
import de.learnlib.api.oracle.MembershipOracle.MealyMembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.observationtable.GenericObservationTable;
import de.learnlib.datastructure.observationtable.Inconsistency;
import de.learnlib.datastructure.observationtable.Row;
import de.learnlib.datastructure.observationtable.writer.ObservationTableASCIIWriter;
import de.learnlib.driver.util.MealySimulatorSUL;
import de.learnlib.filter.statistic.oracle.MealyCounterOracle;
import de.learnlib.mapper.SULMappers;
import de.learnlib.mapper.api.SULMapper;
import de.learnlib.oracle.equivalence.MealyWMethodEQOracle;
import de.learnlib.oracle.membership.SULOracle;
import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.automata.transducers.impl.compact.CompactMealy;
import net.automatalib.automata.transducers.impl.compact.CompactMealyTransition;
import net.automatalib.serialization.dot.GraphDOT;
import net.automatalib.visualization.Visualization;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;

/**
 * This is an example of concrete Mealy machine.
 * 
 * @author Masoud Ebrahimi
 */
@SuppressWarnings("PMD.SystemPrintln")
public final class AbstractMealyLearner1 {

    private static final int EXPLORATION_DEPTH = 4;
    private List<Word<String>> statePrefixMap;
    private Alphabet<String> inputs;
    private MealyMembershipOracle<String, String> sul;
    private MealyCounterOracle<String, String> mqOracle;
    private MealyCounterOracle<String, String> eqMqOracle;
    private MealyWMethodEQOracle<String, String> eqOracle;
    GenericObservationTable<String, Word<String>> table;
    ObservationTableASCIIWriter<String, Word<String>> tableWriter;
    MealyMachine<Integer, String, ?, String> hypothesis;

    public static void main(String[] args) throws IOException {
        AbstractMealyLearner1 experiment = new AbstractMealyLearner1();
        MyMapper mapper = new MyMapper();
        MealySimulatorSUL<Integer, Integer> simulator = new MealySimulatorSUL<>(constructSUL(4));
        SUL<String, String> sul = SULMappers.apply(mapper, simulator);
        experiment.learn(sul);
    }

    protected void learn(SUL<String, String> target) throws IOException {
        // @formatter:off
        inputs      = Alphabets.fromArray(new String[]{"1","2","3"});
        sul         = new SULOracle<>(target);
        mqOracle    = new MealyCounterOracle<>(sul, "Output Queries");
        eqMqOracle  = new MealyCounterOracle<>(sul, "Output Queries during EQ testing");
        eqOracle    = new MealyWMethodEQOracle<>(eqMqOracle, EXPLORATION_DEPTH);
        table       = new GenericObservationTable<>(inputs);
        tableWriter = new ObservationTableASCIIWriter<>();
        // @formatter:on

        System.out.println("=======================================================");
        System.out.println(" Empty Observation Table");
        System.out.println("-------------------------------------------------------");
        tableWriter.write(table, System.out);
        promptEnterKey("initialize the table");

        List<Word<String>> prefixes = initialPrefixes();
        List<Word<String>> suffixes = initialSuffixes(inputs);
        table.initialize(prefixes, suffixes, mqOracle);
        System.out.println("=======================================================");
        System.out.println(" Initialized Observation Table");
        System.out.println("-------------------------------------------------------");
        tableWriter.write(table, System.out);
        promptEnterKey("start learning");

        Integer round = 0;
        DefaultQuery<String, Word<String>> ce;
        while (true) {

            closeTable();
            if(consistentTable()) continue;

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
            if (ce == null) break;

            System.out.println("Counter example: " + ce.toString());
            refineTableColumns(ce);
        }

        System.out.println("=======================================================");
        System.out.println("Final Hypothesis:");
        System.out.println("-------------------------------------------------------");
        System.out.println(mqOracle.getStatisticalData().getSummary());
        System.out.println(eqMqOracle.getStatisticalData().getSummary());
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

    protected static List<Word<String>> initialPrefixes() {
        return Collections.singletonList(Word.epsilon());
    }

    protected static List<Word<String>> initialSuffixes(Alphabet<String> inputs) {
        List<Word<String>> suffixes = new ArrayList<>();
        for (String c : inputs) {
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

        CompactMealy<String, String> model = getPartialModel();
        System.out.println("Open hypothesis model: ");
        GraphDOT.write(model, inputs, System.out);
        System.out.println();

        System.out.println("Intermediate tables: ");
        while (!table.isClosed()) {
            Row<String> unclosed = table.findUnclosedRow();
            Word<String> prefix = unclosed.getLabel();
            table.addShortPrefixes(prefix.prefixes(false), mqOracle.asOracle());
            tableWriter.write(table, System.out);
        }

        model = getPartialModel();
        System.out.println("Closed hypothesis model: ");
        GraphDOT.write(model, inputs, System.out);
        System.out.println();

        System.out.println("Closed table: ");
        tableWriter.write(table, System.out);
    }

    protected boolean consistentTable() throws IOException {
        if (table.isConsistent())
            return false;

        promptEnterKey("make the table consistent");
        System.out.println("=======================================================");
        System.out.println(" Consistent Observation Table");
        System.out.println("-------------------------------------------------------");

        Inconsistency<String> inconsistency;
        while (!table.isConsistent()) {
            inconsistency = table.findInconsistency();
            Word<String> suffix = findDistinguishingSuffix(inconsistency);
            if (suffix != null) {
                List<Word<String>> suffixes = new ArrayList<>(suffix.suffixes(false));
                suffixes.remove(Word.epsilon());
                table.addSuffixes(suffixes, mqOracle);
            }
        }

        System.out.println("Consistent table: ");
        tableWriter.write(table, System.out);
        return true;
    }

    protected void refineTableShortPrefixes(DefaultQuery<String, Word<String>> ce) throws IOException {
        promptEnterKey("refine the hypothesis");
        Word<String> prefix = ce.getPrefix().concat(ce.getSuffix());
        table.addShortPrefixes(prefix.prefixes(false), mqOracle);

        System.out.println("Refined table: ");
        tableWriter.write(table, System.out);
    }

    protected void refineTableColumns(DefaultQuery<String, Word<String>> ce) throws IOException {
        Word<String> word = ce.getInput();

        int state = 0;

        Word<String> query;
        Word<String> output;
        Word<String> suffix = Word.epsilon();

        String lastOutputSymbol;
        String prevLastOutputSymbol = ce.getOutput().lastSymbol();
        for (Word<String> prefix : word.prefixes(false)) {
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
            List<Word<String>> suffixes = new ArrayList<>(suffix.suffixes(false));
            suffixes.remove(Word.epsilon());
            table.addSuffixes(suffixes, mqOracle);
        } else {
            refineTableShortPrefixes(ce);
        }

        System.out.println("Refined table: ");
        tableWriter.write(table, System.out);
    }

    protected CompactMealy<String, String> getPartialModel() {
        CompactMealy<String, String> hypothesis = new CompactMealy<>(inputs);
        Map<Integer, Integer> stateMap = new HashMap();

        List<Row<String>> shortPrefixRows = table.getShortPrefixRows();
        List<Row<String>> allRows = new ArrayList<>(table.getAllRows());
        
        // Creating states
        for (Row<String> row : shortPrefixRows) {
            int state = hypothesis.addIntState();
            stateMap.put(row.getRowContentId(), state);
        }

        // Creating dummy states
        for (Row<String> row : allRows) {
            int id = row.getRowContentId();
            if (stateMap.containsKey(id))
                continue;
            int state = hypothesis.addIntState();
            stateMap.put(id, state);
        }

        // Transition relation
        for (Row<String> row : shortPrefixRows) {
            int state = stateMap.get(row.getRowContentId());
            if (row.getLabel().isEmpty()) {
                hypothesis.setInitial(state, true);
            }

            for (int i = 0; i < inputs.size(); i++) {
                Row<String> successorRow = row.getSuccessor(i);
                int successorState = stateMap.get(successorRow.getRowContentId());
                Word<String> output = table.cellContents(row,
                        table.getSuffixes().indexOf(Word.fromSymbols(inputs.getSymbol(i))));
                CompactMealyTransition<String> t = hypothesis.getTransition(state, inputs.getSymbol(i));
                if (t == null)
                    hypothesis.addTransition(state, inputs.getSymbol(i), successorState, output.firstSymbol());
            }
        }
        return hypothesis;
    }

    protected CompactMealy<String, String> getHypothesisModel() {
        CompactMealy<String, String> hypothesis = new CompactMealy<>(inputs);
        Map<Integer, Integer> stateMap = new HashMap();
        statePrefixMap = new ArrayList<>(table.numberOfRows());

        List<Row<String>> shortPrefixRows = table.getShortPrefixRows();

        // Creating states
        for (Row<String> row : shortPrefixRows) {


            if (stateMap.containsKey(row.getRowContentId()))
                continue;
            int state = hypothesis.addIntState();
            stateMap.put(row.getRowContentId(), state);
            statePrefixMap.add(state, row.getLabel());
            System.out.println(row.getRowContentId() + " mapps to " + state);
        }

        // Transition relation
        for (Row<String> row : shortPrefixRows) {
            int state = stateMap.get(row.getRowContentId());
            if (row.getLabel().isEmpty()) {
                hypothesis.setInitial(state, true);
            }
            System.out.println("State: " + row.getLabel());
            
            for (int i = 0; i < inputs.size(); i++) {
                
                Row<String> successorRow = row.getSuccessor(i);
                System.out.println("Succ: "+successorRow.getLabel());
                System.out.println("Input: "+ inputs.getSymbol(i));
                System.out.println("ContentID: " + successorRow.getRowContentId());

                int successorState = stateMap.get(successorRow.getRowContentId());
                Word<String> output = table.cellContents(row,
                        table.getSuffixes().indexOf(Word.fromSymbols(inputs.getSymbol(i))));
                CompactMealyTransition<String> t = hypothesis.getTransition(state, inputs.getSymbol(i));
                if (t == null)
                    hypothesis.addTransition(state, inputs.getSymbol(i), successorState, output.firstSymbol());
            }
        }
        return hypothesis;
    }

    protected Word<String> findDistinguishingSuffix(Inconsistency<String> inconsistency) {
        int inputIdx = inputs.getSymbolIndex(inconsistency.getSymbol());

        Row<String> firstSuccessor = inconsistency.getFirstRow().getSuccessor(inputIdx);
        Row<String> secondSuccessor = inconsistency.getSecondRow().getSuccessor(inputIdx);

        int numSuffixes = table.getSuffixes().size();

        for (int i = 0; i < numSuffixes; i++) {
            Object firstOutput = table.cellContents(firstSuccessor, i);
            Object secondOutput = table.cellContents(secondSuccessor, i);
            if (!firstOutput.equals(secondOutput)) {
                String sym = inputs.getSymbol(inputIdx);
                Word<String> suffix = table.getSuffixes().get(i);
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
     * @return example mealy
     */
    private static CompactMealy<Integer, Integer> constructSUL(int n) {
        // input alphabet contains Integers 'a'..'b'
        Alphabet<Integer> inputs = Alphabets.integers(1, 1000);

        // create automaton
        CompactMealy<Integer, Integer> mealy = new CompactMealy<>(inputs);

        mealy.addInitialState();
        for(int i=1; i < n - 1; i++)
            mealy.addState();
        mealy.addState();

        for (int state : mealy.getStates()) {
            for (Integer input : inputs) {
                int successor = (state + input) % n;
                int output = state + input;
                mealy.addTransition(state, input, successor, output);
            }
        }
        return mealy;
    }

    private static class MyMapper implements SULMapper<String, String, Integer, Integer>
    {
        Random rand = new Random();
        int min = 1;
        int max = 1000;

        @Override
        public Integer mapInput(String abstractInput) {
            // return rand.nextInt((max - min) + 1) + min;
            return Integer.valueOf(abstractInput);
        }

        @Override
        public String mapOutput(Integer concreteOutput) {
            return Integer.toString(concreteOutput);
        }
    }
}