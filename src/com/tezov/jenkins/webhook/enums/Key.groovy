package com.tezov.jenkins.webhook.enums

import com.tezov.jenkins.webhook.protocol.IsKey

enum Key implements IsKey {
    type,
    repositoryName,
    isSourceBranchDeleted,
    sourceBranch,
    commitMessage,
    author,
    pullRequest
}


