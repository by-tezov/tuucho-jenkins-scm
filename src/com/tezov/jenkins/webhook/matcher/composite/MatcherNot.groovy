package com.tezov.jenkins.webhook.matcher.composite

import com.tezov.jenkins.webhook.WebhookContent

class MatcherNot extends MatcherComposite {

    @Override
    boolean match(WebhookContent content, StringBuilder log) {
        if (matchers.size() != 1) {
            throw new IllegalStateException("MatcherNot requires exactly one matcher.");
        }
        log.append("<not>\n")
        def result = matchers[0].match(content, log)
        log.append("</not>\n")
        return !result
    }
}
