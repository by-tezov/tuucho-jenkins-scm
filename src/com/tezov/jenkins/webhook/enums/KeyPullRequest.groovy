package com.tezov.jenkins.webhook.enums

import com.tezov.jenkins.webhook.protocol.IsKey

enum KeyPullRequest implements IsKey {
    action,
    number,
    state,
    isDraft,
    targetBranch,
    labelAdded,
    labels,
}


