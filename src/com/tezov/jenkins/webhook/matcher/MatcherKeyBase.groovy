package com.tezov.jenkins.webhook.matcher

import com.tezov.jenkins.webhook.protocol.Matcher
import com.tezov.jenkins.webhook.protocol.IsKey

abstract class MatcherKeyBase implements Matcher {

    final IsKey key

    MatcherKeyBase(IsKey key) {
        this.key = key
    }

}
