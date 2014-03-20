package org.kframework.backend.pdmc.pda;

import com.google.common.base.Joiner;

import org.kframework.backend.java.util.Utils;

import java.util.Collections;
import java.util.Stack;

/**
 * The configuration of a pushdown system, consisting of a state and a stack.
 * Internally, this is represented as a pair between a ConfigurationHead and the remainder of the stack.
 * @see org.kframework.backend.pdmc.pda.ConfigurationHead
 *
 * @param <Control> represents the states of the PDS
 * @param <Alphabet> represents the stack alphabet for the PDS
 *
 * @author TraianSF
 */
public class Configuration<Control, Alphabet> {
    private static Stack emptyStack = null;
    ConfigurationHead<Control,Alphabet> head;
    Stack<Alphabet> stack;

    public Configuration(ConfigurationHead<Control, Alphabet> head, Stack<Alphabet> stack) {
        this.head = head;
        this.stack = stack;
    }

    public Configuration(Control control, Stack<Alphabet> stack) {
        if (stack.isEmpty()) {
            head = ConfigurationHead.of(control, null);
            this.stack = emptyStack();
        } else {
            head = ConfigurationHead.of(control, stack.peek());
            if (stack.size() == 1) {
                this.stack = emptyStack();
            } else {
                this.stack = new Stack<>();
                this.stack.addAll(stack);
                this.stack.pop();
            }
        }
    }

    public Configuration(Configuration<Control, Alphabet> configuration, Stack<Alphabet> stack) {
        head = configuration.getHead();
        if (stack.isEmpty()) {
            this.stack = configuration.getStack();
        } else {
            Stack<Alphabet> newStack = new Stack<>();
            newStack.addAll(stack);
            newStack.addAll(configuration.getStack());
            if (!head.isProper()) {
                head = ConfigurationHead.of(head.getState(), newStack.pop());
            }
            this.stack = newStack;
        }
    }

    @Override
    public int hashCode() {
        int hash = 1;
        hash = hash * Utils.HASH_PRIME + head.hashCode();
        hash = hash * Utils.HASH_PRIME + stack.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;

        if (!(obj instanceof Configuration)) return false;

        Configuration configuration = (Configuration) obj;
        return head.equals(configuration.head) && stack.equals(configuration.stack);
     }

    public boolean isFinal() {
        return !head.isProper();
    }

    public ConfigurationHead<Control,Alphabet> getHead() {
        return head;
    }

    public Stack<Alphabet> getStack() {
        return stack;
    }


    public static <Alphabet> Stack<Alphabet> emptyStack() {
        if (emptyStack == null) emptyStack = new Stack<Alphabet>();
        @SuppressWarnings("unchecked")
        Stack<Alphabet> returnStack = (Stack<Alphabet>) emptyStack;
        return returnStack;
    }

    public static Configuration<String, String> of(String confString) {
        assert confString.charAt(0) == '<' : "Configuration must start with '<'.";
        assert confString.charAt(confString.length() - 1) == '>' : "Configuration must start with '>'.";
        String[] strings = confString.substring(1, confString.length() - 1).split(",");
        assert strings.length >= 1 : "Configuration must have a state.";
        assert strings.length <= 2 : "Configuration cannot have more than a stack.";
        String control = strings[0].trim();
        Stack<String> stack = new Stack<>();
        if (strings.length==2) {
            String[] letters = strings[1].trim().split("\\s+");
            for (int i = letters.length; i-- > 0; )
                stack.push(letters[i]);
        }
        return new Configuration<>(control, stack);
    }

    @Override
    public String toString() {
        @SuppressWarnings("unchecked")
        Stack<Alphabet> stack = (Stack<Alphabet>) this.stack.clone();
        if (head.isProper()) stack.add(head.getLetter());
        Collections.reverse(stack);
        StringBuilder builder = new StringBuilder();
        builder.append("<");
        builder.append(head.getState().toString());
        if (!stack.isEmpty()) {
            builder.append(", ");
            Joiner joiner = Joiner.on(" ");
            joiner.appendTo(builder, stack);
        }
        builder.append(">");
        return builder.toString();
    }

    /**
     * Computes the entire stack associated to this configuration by pushing the letter in head to the current stack.
     * @return the full stack
     */
    public Stack<Alphabet> getFullStack() {
        Alphabet letter = head.getLetter();
        if (letter == null) return Configuration.emptyStack();
        @SuppressWarnings("unchecked")
        Stack<Alphabet> stack = (Stack<Alphabet>) this.stack.clone();
        if (stack == null) {
            stack = new Stack<>();
        }
        stack.push(letter);
        return stack;
    }
}
