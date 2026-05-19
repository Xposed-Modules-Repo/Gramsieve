package com.tianqianguai.gramsieve;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.tianqianguai.gramsieve.core.FilterConfig;
import com.tianqianguai.gramsieve.core.FilterDecision;
import com.tianqianguai.gramsieve.core.FilterEngine;
import com.tianqianguai.gramsieve.core.MessageSnapshot;
import com.tianqianguai.gramsieve.core.RuleTextCodec;

import org.junit.Test;

public class FilterEngineTest {
    @Test
    public void exclusionWinsOverKeywordRule() {
        FilterConfig config = FilterConfig.createDefault();
        config.globalRules = RuleTextCodec.parse("t.me/", FilterConfig.RuleMode.KEYWORD);
        config.globalExclusions = RuleTextCodec.parse("sender:trusted_admin", FilterConfig.RuleMode.KEYWORD);
        config.updatedAtEpochMs = 1L;

        MessageSnapshot snapshot = new MessageSnapshot(
                -1001L,
                42L,
                7L,
                "join t.me/spam_now",
                "",
                "",
                "trusted_admin",
                "test group",
                false
        );

        FilterDecision decision = new FilterEngine().evaluate(config, snapshot);
        assertFalse(decision.matched);
        assertTrue(decision.excluded);
    }

    @Test
    public void regexMatchesButtonText() {
        FilterConfig config = FilterConfig.createDefault();
        config.globalRules = RuleTextCodec.parse("button:https?://", FilterConfig.RuleMode.REGEX);
        config.updatedAtEpochMs = 2L;

        MessageSnapshot snapshot = new MessageSnapshot(
                -1001L,
                77L,
                8L,
                "",
                "",
                "Open https://spam.example",
                "merchant_bot",
                "promo room",
                true
        );

        FilterDecision decision = new FilterEngine().evaluate(config, snapshot);
        assertTrue(decision.matched);
        assertEquals(FilterConfig.Action.HIDE, decision.action);
    }

    @Test
    public void perChatOverrideCanIgnoreGlobalRules() {
        FilterConfig config = FilterConfig.createDefault();
        config.globalRules = RuleTextCodec.parse("buy now", FilterConfig.RuleMode.KEYWORD);
        FilterConfig.ChatRuleSet chatRuleSet = config.getOrCreateChatRuleSet(-2002L);
        chatRuleSet.excludeFromGlobal = true;
        chatRuleSet.rules = RuleTextCodec.parse("sender:bad_actor", FilterConfig.RuleMode.KEYWORD);
        config.updatedAtEpochMs = 3L;

        MessageSnapshot sameTextDifferentChat = new MessageSnapshot(
                -2002L,
                18L,
                9L,
                "buy now special offer",
                "",
                "",
                "neutral_sender",
                "ignored chat",
                false
        );

        FilterDecision decision = new FilterEngine().evaluate(config, sameTextDifferentChat);
        assertFalse(decision.matched);
    }

    @Test
    public void senderTargetedRuleMatchesBeforeGenericKeyword() {
        FilterConfig config = FilterConfig.createDefault();
        config.globalRules = RuleTextCodec.parse("sender:promo_bot\nfree", FilterConfig.RuleMode.KEYWORD);
        config.updatedAtEpochMs = 4L;

        MessageSnapshot snapshot = new MessageSnapshot(
                -3003L,
                123L,
                10L,
                "ordinary message",
                "",
                "",
                "promo_bot",
                "channel",
                false
        );

        FilterDecision decision = new FilterEngine().evaluate(config, snapshot);
        assertTrue(decision.matched);
        assertTrue(decision.reason.startsWith("hard match"));
    }

    @Test
    public void chatTargetedRuleMatchesEveryMessageFromMatchingChat() {
        FilterConfig config = FilterConfig.createDefault();
        config.globalRules = RuleTextCodec.parse("chat:吃瓜联盟", FilterConfig.RuleMode.KEYWORD);
        config.updatedAtEpochMs = 5L;

        MessageSnapshot firstMessage = new MessageSnapshot(
                -4004L,
                201L,
                11L,
                "普通聊天内容",
                "",
                "",
                "normal_sender",
                "TG吃瓜联盟总群",
                false
        );
        MessageSnapshot secondMessage = new MessageSnapshot(
                -4004L,
                202L,
                12L,
                "完全不含关键词",
                "",
                "",
                "another_sender",
                "TG吃瓜联盟总群",
                false
        );

        FilterDecision firstDecision = new FilterEngine().evaluate(config, firstMessage);
        FilterDecision secondDecision = new FilterEngine().evaluate(config, secondMessage);

        assertTrue(firstDecision.matched);
        assertTrue(secondDecision.matched);
        assertTrue(firstDecision.reason.startsWith("hard match"));
        assertTrue(secondDecision.reason.startsWith("hard match"));
    }

    @Test
    public void chatTargetedRuleAlsoMatchesDialogId() {
        FilterConfig config = FilterConfig.createDefault();
        config.globalRules = RuleTextCodec.parse("chat:-100123", FilterConfig.RuleMode.KEYWORD);
        config.updatedAtEpochMs = 6L;

        MessageSnapshot snapshot = new MessageSnapshot(
                -1001234567890L,
                203L,
                13L,
                "plain text",
                "",
                "",
                "sender",
                "Some unrelated title",
                false
        );

        FilterDecision decision = new FilterEngine().evaluate(config, snapshot);
        assertTrue(decision.matched);
        assertTrue(decision.reason.startsWith("hard match"));
    }
}
