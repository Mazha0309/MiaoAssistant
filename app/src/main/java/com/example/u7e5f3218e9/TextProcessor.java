package com.example.u7e5f3218e9;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextProcessor {
    private static final Random RANDOM = new Random();
    private static final Pattern SENTENCE_SPLIT_PATTERN = Pattern.compile("([，,。！!？?\\s]+)");

    /**
     * 通用文本改写：按配置顺序应用「自定义替换规则」→「断句追加」→「句末颜文字」。
     */
    public static String process(String original, CatConfig config) {
        if (original == null || original.trim().isEmpty()) {
            return original;
        }
        String text = original.trim();

        // 1. 按顺序应用自定义替换规则（后一条规则可作用于前一条的结果）
        if (config.rules != null) {
            for (CatConfig.Rule rule : config.rules) {
                if (rule == null || rule.from.isEmpty()) {
                    continue;
                }
                text = text.replace(rule.from, rule.to);
            }
        }

        // 2. 断句追加（原"断句加喵"，后缀可在界面自定义）
        if (config.enableAppend) {
            text = appendPerSentence(text, config.appendText);
        }

        // 3. 句末随机颜文字
        if (config.enableRandomEmoticon) {
            String emoticon = getRandomEmoticon(config);
            if (emoticon != null && !emoticon.isEmpty()) {
                text = text + " " + emoticon;
            }
        }
        return text;
    }

    /** 按标点分句，在每句末尾追加指定的 suffix */
    private static String appendPerSentence(String text, String suffix) {
        String s = (suffix == null) ? "" : suffix;
        List<String> parts = new ArrayList<>();
        List<String> separators = new ArrayList<>();
        Matcher matcher = SENTENCE_SPLIT_PATTERN.matcher(text);
        int lastEnd = 0;
        while (matcher.find()) {
            parts.add(text.substring(lastEnd, matcher.start()));
            separators.add(matcher.group(1));
            lastEnd = matcher.end();
        }
        if (lastEnd < text.length()) {
            parts.add(text.substring(lastEnd));
        } else if (!parts.isEmpty() && lastEnd == text.length()) {
            parts.add("");
        }
        if (parts.isEmpty()) {
            parts.add(text);
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i).trim();
            if (!part.isEmpty()) {
                result.append(part);
                result.append(s);
            }
            if (i < separators.size()) {
                result.append(separators.get(i));
            }
        }
        String resultStr = result.toString().trim();
        if (resultStr.isEmpty()) {
            return text + s;
        }
        return resultStr;
    }

    private static String getRandomEmoticon(CatConfig config) {
        String[] emoticons = config.getActiveEmoticons();
        if (emoticons == null || emoticons.length == 0) {
            emoticons = CatConfig.BUILTIN_EMOTICONS;
        }
        return emoticons.length == 0 ? "" : emoticons[RANDOM.nextInt(emoticons.length)];
    }

    /** 便捷入口：仅开启断句加"喵"与随机颜文字，无自定义替换规则 */
    public static String process(String original) {
        CatConfig defaults = new CatConfig();
        defaults.enableAppend = true;
        defaults.appendText = "喵";
        defaults.enableRandomEmoticon = true;
        defaults.customEmoticons = new String[0];
        defaults.rules = new ArrayList<>();
        return process(original, defaults);
    }
}