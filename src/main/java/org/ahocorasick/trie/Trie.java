package org.ahocorasick.trie;

import org.ahocorasick.trie.candidate.EmitCandidateFlushHandler;
import org.ahocorasick.trie.candidate.EmitCandidateHolder;
import org.ahocorasick.trie.candidate.NonOverlappingEmitCandidateHolder;
import org.ahocorasick.trie.candidate.OverlappingEmitCandidateHolder;
import org.ahocorasick.trie.handler.DefaultEmitHandler;
import org.ahocorasick.trie.handler.EmitHandler;
import org.ahocorasick.trie.handler.FirstMatchHandler;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 *
 * Based on the Aho-Corasick white paper, Bell technologies: http://cr.yp.to/bib/1975/aho.pdf
 * @author Robert Bor
 */
public class Trie {

    private TrieConfig trieConfig;

    private State rootState;

    private Trie(TrieConfig trieConfig) {
        this.trieConfig = trieConfig;
        this.rootState = new State();
    }

    private void addKeyword(String keyword) {
        if (keyword == null || keyword.length() == 0) {
            return;
        }
        State currentState = this.rootState;
        for (Character character : keyword.toCharArray()) {
            if (trieConfig.isCaseInsensitive()) {
                character = Character.toLowerCase(character);
            }
            currentState = currentState.addState(character);
        }
        currentState.addEmit(trieConfig.isCaseInsensitive() ? keyword.toLowerCase() : keyword);
    }

    public Collection<Token> tokenize(String text) {
        return new Tokenizer(parseText(text), text).tokenize();
    }

    @SuppressWarnings("unchecked")
    public Collection<Emit> parseText(CharSequence text) {
        DefaultEmitHandler emitHandler = new DefaultEmitHandler();
        parseText(text, emitHandler);
        return emitHandler.getEmits();
    }

	public boolean containsMatch(CharSequence text) {
		Emit firstMatch = firstMatch(text);
		return firstMatch != null;
	}

    public Emit firstMatch(CharSequence text) {
        FirstMatchHandler emitHandler = new FirstMatchHandler();
        parseText(text, emitHandler);
        return emitHandler.getFirstMatch();
    }

    public void parseText(CharSequence text, EmitHandler emitHandler) {

        final EmitCandidateHolder emitCandidateHolder = this.trieConfig.isAllowOverlaps() ?
                new OverlappingEmitCandidateHolder() :
                new NonOverlappingEmitCandidateHolder();

        final EmitCandidateFlushHandler flushHandler = new EmitCandidateFlushHandler(emitHandler, emitCandidateHolder);

        State currentState = this.rootState;
        for (int position = 0; position < text.length(); position++) {

            if (flushHandler.stop()) {
                return;
            }

            Character character = text.charAt(position);
            if (trieConfig.isCaseInsensitive()) {
                character = Character.toLowerCase(character);
            }
            currentState = getState(currentState, character, flushHandler);

            Collection<String> emits = currentState.emit();
            for (String emit : emits) {
                int start = position - emit.length() + 1;
                if (!trieConfig.isOnlyWholeWords() || isWholeWord(text, start, position)) {
                    emitCandidateHolder.addCandidate(new Emit(start, position, emit));
                }
            }

        }
        flushHandler.flush();
    }

    public static boolean isWholeWord(CharSequence text, int start, int end) {
        return (start == 0 || Character.isWhitespace(text.charAt(start - 1))) &&
               (end == text.length() - 1 || Character.isWhitespace(text.charAt(end + 1)));
    }

    private State getState(State currentState, Character character, EmitCandidateFlushHandler flushHandler) {
        State newCurrentState = currentState.nextState(character);
        while (newCurrentState == null) {
            currentState = currentState.failure(flushHandler);
            newCurrentState = currentState.nextState(character);
        }
        return newCurrentState;
    }

    private void constructFailureStates() {
        Queue<State> queue = new LinkedBlockingDeque<>();

        // First, set the fail state of all depth 1 states to the root state
        for (State depthOneState : this.rootState.getStates()) {
            depthOneState.setFailure(this.rootState);
            queue.add(depthOneState);
        }

        // Second, determine the fail state for all depth > 1 state
        while (!queue.isEmpty()) {
            State currentState = queue.remove();

            for (Character transition : currentState.getTransitions()) {
                State targetState = currentState.nextState(transition);
                queue.add(targetState);

                State traceFailureState = currentState.failure();
                while (traceFailureState.nextState(transition) == null) {
                    traceFailureState = traceFailureState.failure();
                }
                State newFailureState = traceFailureState.nextState(transition);
                targetState.setFailure(newFailureState);
                targetState.addEmit(newFailureState.emit());
            }
        }
    }

    public static TrieBuilder builder() {
        return new TrieBuilder();
    }

    public static class TrieBuilder {

        private TrieConfig trieConfig = new TrieConfig();

        private Trie trie = new Trie(trieConfig);

        private TrieBuilder() {}

        public TrieBuilder caseInsensitive() {
            this.trieConfig.setCaseInsensitive(true);
            return this;
        }

        public TrieBuilder removeOverlaps() {
            this.trieConfig.setAllowOverlaps(false);
            return this;
        }

        public TrieBuilder onlyWholeWords() {
            this.trieConfig.setOnlyWholeWords(true);
            return this;
        }

        public TrieBuilder addKeyword(String keyword) {
            trie.addKeyword(keyword);
            return this;
        }

        public Trie build() {
            trie.constructFailureStates();
            return trie;
        }
    }
}
