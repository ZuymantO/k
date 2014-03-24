package org.kframework.backend.pdmc.automaton;

import org.kframework.backend.java.util.Utils;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Traian
 */
public class TransitionIndex<State, Alphabet> implements StateLetterPair<State,Alphabet> {
    private static Map<Object, Map<Object, TransitionIndex>> extendedCache;
    private static Map<Object, TransitionIndex> basicCache;
    private final State state;

    @Override
    public Alphabet getLetter() {
        return letter;
    }

    @Override
    public State getState() {
        return state;
    }

    private final Alphabet letter;

    private TransitionIndex(State state, Alphabet letter) {
        this.state = state;
        this.letter = letter;
    }


    @Override
    public int hashCode() {
        int hash = 1;
        hash = hash * Utils.HASH_PRIME + state.hashCode();
        if (letter != null) hash = hash * Utils.HASH_PRIME + letter.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        /* the cache ensures uniqueness of logically equal object instances */
        return  this == obj;
    }

    public static <State, Alphabet> TransitionIndex<State, Alphabet> of(State start, Alphabet letter) {
        if (letter == null) return of(start);
        if (extendedCache == null) extendedCache = new HashMap<>();
        Map<Object,TransitionIndex> map = extendedCache.get(start);
        if (map == null) {
            map = new HashMap<>();
            extendedCache.put(start, map);
        }
        @SuppressWarnings("unchecked")
        TransitionIndex<State, Alphabet> index = (TransitionIndex<State, Alphabet>) map.get(letter);
        if (index == null) {
            index = new TransitionIndex<>(start, letter);
            map.put(letter, index);
        }
        return index;
    }

    private static <State, Alphabet> TransitionIndex<State, Alphabet> of(State start) {
        if (basicCache == null) {
            basicCache = new HashMap<>();
        }
        @SuppressWarnings("unchecked")
        TransitionIndex<State, Alphabet> index = (TransitionIndex<State, Alphabet>) basicCache.get(start);
        if (index == null) {
            index = new TransitionIndex<>(start, null);
            basicCache.put(start, index);
        }
        return index;

    }
}
