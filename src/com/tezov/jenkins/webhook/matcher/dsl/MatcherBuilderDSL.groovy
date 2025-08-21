package com.tezov.jenkins.webhook.matcher.dsl

import com.tezov.jenkins.webhook.WebhookContent
import com.tezov.jenkins.webhook.protocol.Matcher
import com.tezov.jenkins.webhook.matcher.composite.MatcherComposite

class MatcherBuilderDSL {
    private Matcher root = null
    private Closure logClosure = null
    private Closure onSuccessClosure = null

    void add(Matcher matcher) {
        switch (root?.getClass()) {
            case null:
                root = matcher
                break
            case MatcherComposite:
                root.add(matcher)
                break
            default:
                throw new IllegalStateException("MatcherGroup: root is not a composite matcher. Use 'and' or 'or' to add multiple conditions.")
        }
    }

    void setLogClosure(Closure closure) {
        this.logClosure = closure
    }

    void setOnSuccessClosure(Closure closure) {
        this.onSuccessClosure = closure
    }

    boolean match(WebhookContent content) {
        StringBuilder log = new StringBuilder()
        log.append("** MATCHER **\n")
        boolean result = root?.match(content, log) ?: false
        if (!result) {
            log.append("!! FAILED !!")
            logClosure?.call(log.toString())
            return false
        }
        log.append("** SUCCESS **")
        logClosure?.call(log.toString())
        onSuccessClosure?.call()
        return true
    }
}