package org.kframework.backend.pdmc.automaton;

import com.google.common.base.Joiner;

import java.util.*;

/**
 * This class defines a nondeterministic finite automaton (NFA).  It keeps the transition function of the automatonas a
 * map indexed on pairs of states and alphabet letters having as values the set of transitions corresponding to that
 * index.
 * It is parameterized on the class for states and the one for the alphabet.
 *
 * @param <State>  represents the states of the automaton
 * @param <Alphabet>  represents the alphabet of the automaton
 * @author Traian
 */
public class BasicAutomaton<State, Alphabet> implements AutomatonInterface<State, Alphabet> {
    /**
     * Indexing datastructure for the automaton's transition relation.
     * Given a {@see TransitionIndex} as a pair between a state and a letter, it yields all
     * transitions originating in that pair (note this is a NFA).
     */
    private final Map<TransitionIndex<State, Alphabet>,
                Set<Transition<State, Alphabet>>> deltaIndex;

    /**
     * Accessor for the {@code deltaIndex}
     * @return deltaIndex representing the delta mapping of the automaton.
     */
    public Map<TransitionIndex<State, Alphabet>, Set<Transition<State, Alphabet>>> getDeltaIndex() {
        return deltaIndex;
    }


    private final State initialState;
    private final Set<State> finalStates;
    private final Set<Alphabet> letters;


    /**
     * Retrieves the set of automaton transitions corresponding to a  state-letter pair.
     * @param state the first argument of the NFA transition function
     * @param letter the second argument of the NFA transition function
     * @return the (possibly empty) set of transitions corresponding to given state and letter
     */
    @Override
    public Set<Transition<State, Alphabet>> getTransitions(State state, Alphabet letter) {
        Set<Transition<State, Alphabet>> transitions = deltaIndex.get(
                TransitionIndex.of(state, letter) );
        if (transitions == null) transitions = Collections.emptySet();
        return transitions;
    }

    @Override
    public State initialState() {
        return initialState;
    }

    @Override
    public Set<State> getFinalStates() {
        return finalStates;
    }

    public BasicAutomaton(Collection<Transition<State, Alphabet>> delta,
                          State initialState,
                          Collection<State> finalStates) {
        this.initialState = initialState;
        this.finalStates = new HashSet<>(finalStates);
        this.letters = new HashSet<>();
        deltaIndex = new HashMap<>();
        for (Transition<State, Alphabet> transition : delta) {
            @SuppressWarnings("unchecked")
            TransitionIndex<State, Alphabet> index = transition.getIndex();
            if(index.getLetter() != null) {
                letters.add(index.getLetter());
            }
            Set<Transition<State, Alphabet>> transitions = deltaIndex.get(index);
            if (transitions == null) {
                transitions = new HashSet<>();
                deltaIndex.put(index, transitions);
            }
            transitions.add(transition);
        }

    }

    public BasicAutomaton(Map<TransitionIndex<State, Alphabet>, Set<Transition<State, Alphabet>>> deltaIndex,
                          State initialState,
                          Set<State> finalStates) {
        this.letters = new HashSet<>();
        for (TransitionIndex<State, Alphabet> index : deltaIndex.keySet()) {
            if (index.getLetter()!= null) {
                letters.add(index.getLetter());
            }
        }
        this.deltaIndex = deltaIndex;
        this.initialState = initialState;
        this.finalStates = finalStates;
    }

    public Collection<Set<Transition<State, Alphabet>>> getTransitions() {
        return deltaIndex.values();
    }
 
    @Override
    public String toString() {
        Joiner joiner = Joiner.on(";\n");
        List<StringBuilder> builders = new ArrayList<>();
        for (Set<Transition<State, Alphabet>> transitions : deltaIndex.values()) {
            StringBuilder builder = new StringBuilder();
            joiner.appendTo(builder, transitions);
            builders.add(builder);
        }
        builders.add(new StringBuilder(initialState.toString()));
        Joiner joiner1 = Joiner.on(" ");
        StringBuilder builder = new StringBuilder();
        joiner1.appendTo(builder, finalStates);
        builders.add(builder);
        builder = new StringBuilder();
        joiner.appendTo(builder, builders);
        return builder.toString();
    }

    /**
     * Given an initial state, a final state and the transition relation of an automaton, find a path of transitions
     * between them.
     * @param initialState the state to begin from
     * @param finalState  the final state to reach
     * @return a list of transitions describing a path from initialState to finalState or null if there is no such list.
     */
    public Deque<Transition<State, Alphabet>> getPath(State initialState, State finalState) {
        if (initialState.equals(finalState)) {
            return new ArrayDeque<>();
        }
        // This algorithm is a simple BFS traversal of the transition graph.
        // toProcess keeps the queue of the states which have yet to be processed
        Deque<State> toProcess = new ArrayDeque<>();
        // considered remembers states which have already been seen.
        // To save the return path, is organized as a map, assigning to each state the transition through which it was
        // reached.
        Map<State, Transition<State, Alphabet>> considered = new HashMap<>();
        toProcess.add(initialState);
        considered.put(initialState, null);
        return getPathBFS(finalState, toProcess, considered);
    }

    /**
     * Given an initial state, a final state and an initial letter, find a path of transitions
     * between them such that first transition is labeled with the initial letter.
     * @param initialState the state to begin from
     * @param initialLetter the letter to start the path on
     * @param finalState  the final state to reach
     * @return a list of transitions describing a path from initialState to finalState or null if there is no such list.
     */
    public Deque<Transition<State, Alphabet>> getPath(State initialState, Alphabet initialLetter, State finalState) {
        // We seed the algorithm with the initialState and those reachable  from it following initialLetter
        Deque<State> toProcess = new ArrayDeque<>();
        Map<State, Transition<State, Alphabet>> considered = new HashMap<>();
        considered.put(initialState, null);

        // Use only states reachable from initialState via initialLetter for the first step.
        Deque<Transition<State, Alphabet>> result =
                getPathBFSStep(getTransitions(initialState, initialLetter), finalState, considered, toProcess);
        // If path was found in one step, return it
        if (result != null) return result;
        // If path was not found yet, continue with usual BFS algorithm
        return getPathBFS(finalState, toProcess, considered);
    }

    private Deque<Transition<State, Alphabet>> getPathBFS(State finalState,
                                                          Deque<State> toProcess,
                                                          Map<State, Transition<State, Alphabet>> considered) {
        Deque<Transition<State, Alphabet>> result;
        State next;
        while (!toProcess.isEmpty()) {
            next = toProcess.remove();
            for (Set<Transition<State, Alphabet>> transitions: getFrontTransitions(next)) {
                result = getPathBFSStep(transitions, finalState, considered, toProcess);
                if (result !=null) return result;
            }
        }
        // If this point was reached, it means finalState is not reachable from initialState.
        return null;
    }

    private Deque<Transition<State, Alphabet>> getPathBFSStep(Set<Transition<State, Alphabet>> transitions, State finalState, Map<State, Transition<State, Alphabet>> considered, Deque<State> toProcess) {
        for (Transition<State, Alphabet> transition : transitions) {
            State endState = transition.getEnd();
            if (endState.equals(finalState)) {
                // if the final state is reached, compute the path to it by walking back on the transitions.
                Deque<Transition<State, Alphabet>> result = new ArrayDeque<>();
                while (transition != null) {
                    result.push(transition);
                    transition = considered.get(transition.getStart());
                }
                return result;
            }
            if (considered.containsKey(endState)) continue;
            considered.put(endState, transition);
            toProcess.add(endState);
        }
        return null;
    }

    /**
     * @param state the state for which forward transitions are needed
     * @return the collection of all sets of transitions originating in state.
     */
    private Collection<Set<Transition<State, Alphabet>>> getFrontTransitions(State state) {
        ArrayList<Set<Transition<State, Alphabet>>> result = new ArrayList<>();
        for (Alphabet letter : letters) {
            Set<Transition<State, Alphabet>> transitions = getTransitions(state, letter);
            if (!transitions.isEmpty()) {
                result.add(transitions);
            }
        }
        return result;
    }
}
