package com.tianqianguai.gramsieve.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class FilterEngine {
    private long compiledToken = Long.MIN_VALUE;
    private List<CompiledRule> globalRules = List.of();
    private List<CompiledRule> globalExclusions = List.of();

    public synchronized FilterDecision evaluate(FilterConfig config, MessageSnapshot snapshot) {
        if (config == null || snapshot == null || !config.enabled) {
            return FilterDecision.allow();
        }

        FilterConfig.ChatRuleSet chatRuleSet = config.getChatRuleSet(snapshot.dialogId);
        if (chatRuleSet != null && !chatRuleSet.enabled) {
            return FilterDecision.allow();
        }

        ensureCompiled(config);

        List<CompiledRule> activeRules = new ArrayList<>();
        List<CompiledRule> activeExclusions = new ArrayList<>();
        boolean useGlobal = chatRuleSet == null || !chatRuleSet.excludeFromGlobal;
        if (useGlobal) {
            activeRules.addAll(globalRules);
            activeExclusions.addAll(globalExclusions);
        }
        if (chatRuleSet != null) {
            activeRules.addAll(compile(chatRuleSet.rules));
            activeExclusions.addAll(compile(chatRuleSet.exclusions));
        }

        CompiledRule exclusion = firstMatch(activeExclusions, snapshot);
        if (exclusion != null) {
            return FilterDecision.excluded(exclusion.id, "excluded by " + exclusion.id);
        }

        CompiledRule hardRule = firstMatch(filterByTarget(activeRules, true), snapshot);
        if (hardRule != null) {
            return FilterDecision.matched(config.action, hardRule.id, "hard match " + hardRule.id);
        }

        CompiledRule regexRule = firstMatch(filterByModeAndTarget(activeRules, FilterConfig.RuleMode.REGEX, false), snapshot);
        if (regexRule != null) {
            return FilterDecision.matched(config.action, regexRule.id, "regex match " + regexRule.id);
        }

        CompiledRule keywordRule = firstMatch(filterByModeAndTarget(activeRules, FilterConfig.RuleMode.KEYWORD, false), snapshot);
        if (keywordRule != null) {
            return FilterDecision.matched(config.action, keywordRule.id, "keyword match " + keywordRule.id);
        }

        return FilterDecision.allow();
    }

    private void ensureCompiled(FilterConfig config) {
        if (compiledToken == config.updatedAtEpochMs) {
            return;
        }
        globalRules = compile(config.globalRules);
        globalExclusions = compile(config.globalExclusions);
        compiledToken = config.updatedAtEpochMs;
    }

    private static List<CompiledRule> filterByTarget(List<CompiledRule> rules, boolean senderOrChatOnly) {
        List<CompiledRule> filtered = new ArrayList<>();
        for (CompiledRule rule : rules) {
            if (senderOrChatOnly) {
                if (rule.target == FilterConfig.RuleTarget.SENDER || rule.target == FilterConfig.RuleTarget.CHAT) {
                    filtered.add(rule);
                }
            }
        }
        return filtered;
    }

    private static List<CompiledRule> filterByModeAndTarget(List<CompiledRule> rules, FilterConfig.RuleMode mode, boolean senderOrChatOnly) {
        List<CompiledRule> filtered = new ArrayList<>();
        for (CompiledRule rule : rules) {
            if (rule.mode != mode) {
                continue;
            }
            boolean hardTarget = rule.target == FilterConfig.RuleTarget.SENDER || rule.target == FilterConfig.RuleTarget.CHAT;
            if (senderOrChatOnly == hardTarget) {
                filtered.add(rule);
            }
        }
        return filtered;
    }

    private static List<CompiledRule> compile(List<FilterConfig.RuleSpec> rules) {
        List<CompiledRule> compiled = new ArrayList<>();
        for (FilterConfig.RuleSpec rule : rules) {
            if (rule == null || !rule.enabled || rule.pattern == null || rule.pattern.isBlank()) {
                continue;
            }
            compiled.add(new CompiledRule(rule));
        }
        return compiled;
    }

    private static CompiledRule firstMatch(List<CompiledRule> rules, MessageSnapshot snapshot) {
        for (CompiledRule rule : rules) {
            if (rule.matches(snapshot)) {
                return rule;
            }
        }
        return null;
    }

    private static final class CompiledRule {
        final String id;
        final FilterConfig.RuleMode mode;
        final FilterConfig.RuleTarget target;
        final String rawPattern;
        final String normalizedKeyword;
        final Pattern regex;
        final boolean caseSensitive;

        CompiledRule(FilterConfig.RuleSpec rule) {
            id = rule.id == null ? "" : rule.id;
            mode = rule.mode == null ? FilterConfig.RuleMode.KEYWORD : rule.mode;
            target = rule.target == null ? FilterConfig.RuleTarget.ANY : rule.target;
            rawPattern = rule.pattern.trim();
            caseSensitive = rule.caseSensitive;
            if (mode == FilterConfig.RuleMode.REGEX) {
                Pattern compiled = null;
                try {
                    int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
                    compiled = Pattern.compile(rawPattern, flags);
                } catch (PatternSyntaxException ignored) {
                    compiled = null;
                }
                regex = compiled;
                normalizedKeyword = "";
            } else {
                regex = null;
                normalizedKeyword = caseSensitive ? rawPattern : rawPattern.toLowerCase(Locale.ROOT);
            }
        }

        boolean matches(MessageSnapshot snapshot) {
            List<String> values = snapshot.valuesFor(target);
            for (String value : values) {
                if (mode == FilterConfig.RuleMode.REGEX) {
                    if (regex != null && regex.matcher(value).find()) {
                        return true;
                    }
                } else {
                    String haystack = caseSensitive ? value : value.toLowerCase(Locale.ROOT);
                    if (haystack.contains(normalizedKeyword)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
