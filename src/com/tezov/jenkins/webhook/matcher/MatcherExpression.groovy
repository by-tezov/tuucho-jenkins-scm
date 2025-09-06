package com.tezov.jenkins.webhook.matcher

import com.tezov.jenkins.webhook.WebhookContent
import com.tezov.jenkins.webhook.protocol.IsKey

class MatcherExpression extends MatcherKeyBase {
    private final Closure closure

    static MatcherExpression with(IsKey key, Closure<Boolean> closure) {
        return new MatcherExpression(key, closure)
    }

    MatcherExpression(IsKey key, Closure<Boolean> closure) {
        super(key)
        this.closure = closure
    }

    @Override
    boolean match(WebhookContent content, StringBuilder log) {
        if (key) {
            def value = content[this.key]
            Boolean result = closure.call(value)
            log.append("${this.key}: ${value} :EXPRESSION: -> ${result}\n")
            return result
        } else {
            Boolean result = closure.call()
            log.append(":EXPRESSION: -> ${result}\n")
            return result
        }
    }
}
