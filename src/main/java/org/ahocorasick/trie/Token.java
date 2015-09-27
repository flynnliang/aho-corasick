package org.ahocorasick.trie;

public abstract class Token {

    private String fragment;

    public Token(String fragment) {
        this.fragment = fragment;
    }

    public String getFragment() {
        return this.fragment;
    }

    public abstract boolean isMatch();

    public boolean isWholeWord() {
        return false;
    }

    public abstract Emit getEmit();

}
