package com.tezov.jenkins.webhook.matcher.composite

import com.tezov.jenkins.webhook.WebhookContent
import com.tezov.jenkins.webhook.protocol.Matcher

class MatcherAnd extends MatcherComposite {

    @Override
    boolean match(WebhookContent content, StringBuilder log) {
        log.append("<and>\n")
        for (Matcher matcher : matchers) {
            if (!matcher.match(content, log)) {
                log.append("!! Failed !!</and>\n")
                return false
            }
        }
        log.append("</and>\n")
        return true
    }
}