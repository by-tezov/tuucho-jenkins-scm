import com.tezov.jenkins.webhook.matcher.dsl.MatcherBuilderDSL
import com.tezov.jenkins.webhook.matcher.dsl.MatcherDSL
import com.tezov.jenkins.webhook.WebhookContent

boolean call(WebhookContent content, Closure closure) {
    def builder = new MatcherBuilderDSL()
    def matcher = new MatcherDSL(builder)
    closure.delegate = matcher
    closure.resolveStrategy = Closure.DELEGATE_FIRST
    closure.call()
    return builder.match(content)
}