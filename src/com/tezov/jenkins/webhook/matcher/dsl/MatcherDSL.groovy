package com.tezov.jenkins.webhook.matcher.dsl

import com.tezov.jenkins.webhook.protocol.Matcher
import com.tezov.jenkins.webhook.matcher.MatcherExpression
import com.tezov.jenkins.webhook.matcher.MatcherIsTrue
import com.tezov.jenkins.webhook.matcher.MatcherIsFalse
import com.tezov.jenkins.webhook.matcher.MatcherIsNull
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

    void expression(String description, Closure<Boolean> closure) {
        def matcher = MatcherExpression.with(description, closure)
        _builder.add(matcher)
    }

    void expression(IsKey key, Closure<Boolean> closure) {
        def matcher = MatcherExpression.with(key, closure)
        _builder.add(matcher)
    }

    void isTrue(IsKey key) {
        def matcher = MatcherIsTrue.with(key)
        _builder.add(matcher)
    }

    void isFalse(IsKey key) {
        def matcher = MatcherIsFalse.with(key)
        _builder.add(matcher)
    }

    void isNull(IsKey key) {
        def matcher = MatcherIsNull.with(key)
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

    void expression(String description, Closure<Boolean> closure) {
        def matcher = MatcherExpression.with(description, closure)
        _matcher.add(matcher)
    }

    void expression(IsKey key, Closure<Boolean> closure) {
        def matcher = MatcherExpression.with(key, closure)
        _matcher.add(matcher)
    }

    void isTrue(IsKey key) {
        def matcher = MatcherIsTrue.with(key)
        _matcher.add(matcher)
    }

    void isFalse(IsKey key) {
        def matcher = MatcherIsFalse.with(key)
        _matcher.add(matcher)
    }

    void isNull(IsKey key) {
        def matcher = MatcherIsNull.with(key)
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



