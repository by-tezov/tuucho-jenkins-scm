package com.tezov.jenkins.webhook.matcher.composite

import com.tezov.jenkins.webhook.protocol.Matcher

abstract class MatcherComposite implements Matcher {
    protected List<Matcher> matchers = []

    void add(Matcher matcher) {
        matchers.add(matcher)
    }
}