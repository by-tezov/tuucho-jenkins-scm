package com.tezov.jenkins.webhook.matcher

import com.tezov.jenkins.webhook.WebhookContent
import com.tezov.jenkins.webhook.protocol.IsKey

class MatcherExpression extends MatcherKeyBase {
    private final String description
    private final Closure closure

    static MatcherExpression with(IsKey key, Closure<Boolean> closure) {
        return new MatcherExpression(null, key, closure)
    }

    static MatcherExpression with(String description, Closure<Boolean> closure) {
        return new MatcherExpression(description, null, closure)
    }

    MatcherExpression(String description, IsKey key, Closure<Boolean> closure) {
        super(key)
        this.description = description
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
            log.append("${description} :EXPRESSION: -> ${result}\n")
            return result
        }
    }
}
