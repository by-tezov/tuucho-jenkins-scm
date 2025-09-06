package com.tezov.jenkins.webhook.matcher.dsl

import com.tezov.jenkins.webhook.protocol.Matcher
import com.tezov.jenkins.webhook.matcher.MatcherExpression
import com.tezov.jenkins.webhook.matcher.MatcherExact
import com.tezov.jenkins.webhook.matcher.MatcherContains
import com.tezov.jenkins.webhook.matcher.MatcherRegex
import com.tezov.jenkins.webhook.matcher.composite.MatcherComposite
import com.tezov.jenkins.webhook.matcher.composite.MatcherAnd
import com.tezov.jenkins.webhook.matcher.composite.MatcherOr
import com.tezov.jenkins.webhook.matcher.composite.MatcherNot
import com.tezov.jenkins.webhook.protocol.IsKey
import com.tezov.jenkins.webhook.protocol.HasValue

class MatcherDSL {
    private MatcherBuilderDSL _builder

    MatcherDSL(MatcherBuilderDSL builder) {
        this._builder = builder
    }

    void log(Closure closure) {
        _builder.setLogClosure(closure)
    }

    void onSuccess(Closure closure) {
        _builder.setOnSuccessClosure(closure)
    }

    void expression(Closure<Boolean> closure) {
        def matcher = MatcherExpression.with(null, closure)
        _builder.add(matcher)
    }

    void expression(IsKey key, Closure<Boolean> closure) {
        def matcher = MatcherExpression.with(key, closure)
        _builder.add(matcher)
    }

    void exact(IsKey key, HasValue<?> hasValue) {
        def matcher = MatcherExact.with(key, hasValue)
        _builder.add(matcher)
    }

    void exact(IsKey key, String value) {
        def matcher = MatcherExact.with(key, value)
        _builder.add(matcher)
    }

    void exact(IsKey key, Boolean value) {
        def matcher = MatcherExact.with(key, value)
        _builder.add(matcher)
    }

    void contains(IsKey key, HasValue<?> hasValue) {
        def matcher = MatcherContains.with(key, hasValue)
        _builder.add(matcher)
    }

    void contains(IsKey key, String value) {
        def matcher = MatcherContains.with(key, value)
        _builder.add(matcher)
    }

    void regex(IsKey key, String pattern) {
        def matcher = MatcherRegex.with(key, pattern)
        _builder.add(matcher)
    }

    void regexOr(IsKey key, List<HasValue<String>> hasValues) {
        def matcher = MatcherRegex.withOr(key, hasValues)
        _builder.add(matcher)
    }

    void not(Closure closure) {
        delegate(closure, new MatcherNot())
    }

    void and(Closure closure) {
        delegate(closure, new MatcherAnd())
    }

    void or(Closure closure) {
        delegate(closure, new MatcherOr())
    }

    private delegate(Closure<Matcher> closure, MatcherComposite matcherComposite) {
        def matcherCompositeDSL = new MatcherCompositeDSL(matcherComposite)
        closure.delegate = matcherCompositeDSL
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
        _builder.add(matcherComposite)
    }
}

class MatcherCompositeDSL {
    private MatcherComposite _matcher

    MatcherCompositeDSL(MatcherComposite matcher) {
        this._matcher = matcher
    }

    void expression(Closure<Boolean> closure) {
        def matcher = MatcherExpression.with(null, closure)
        _matcher.add(matcher)
    }

    void expression(IsKey key, Closure<Boolean> closure) {
        def matcher = MatcherExpression.with(key, closure)
        _matcher.add(matcher)
    }

    void exact(IsKey key, HasValue<?> hasValue) {
        def matcher = MatcherExact.with(key, hasValue)
        _matcher.add(matcher)
    }

    void exact(IsKey key, String value) {
        def matcher = MatcherExact.with(key, value)
        _matcher.add(matcher)
    }

    void exact(IsKey key, Boolean value) {
        def matcher = MatcherExact.with(key, value)
        _matcher.add(matcher)
    }

    void contains(IsKey key, HasValue<?> hasValue) {
        def matcher = MatcherContains.with(key, hasValue)
        _matcher.add(matcher)
    }

    void contains(IsKey key, String value) {
        def matcher = MatcherContains.with(key, value)
        _matcher.add(matcher)
    }

    void regex(IsKey key, String pattern) {
        def matcher = MatcherRegex.with(key, pattern)
        _matcher.add(matcher)
    }

    void regexOr(IsKey key, List<HasValue<String>> hasValues) {
        def matcher = MatcherRegex.withOr(key, hasValues)
        _matcher.add(matcher)
    }

    void not(Closure closure) {
        delegate(closure, new MatcherNot())
    }

    void and(Closure closure) {
        delegate(closure, new MatcherAnd())
    }

    void or(Closure closure) {
        delegate(closure, new MatcherOr())
    }

    private delegate(Closure<Matcher> closure, MatcherComposite matcherComposite) {
        def matcherCompositeDSL = new MatcherCompositeDSL(matcherComposite)
        closure.delegate = matcherCompositeDSL
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
        _matcher.add(matcherComposite)
    }
}



