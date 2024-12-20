package org.techbd.util;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum ScreeningAnswer {

    NEVER(1, "LA6270-8", "Never"),
    RARELY(2, "LA10066-1", "Rarely"),
    SOMETIMES(3, "LA10082-8", "Sometimes"),
    FAIRLY_OFTEN(4, "LA16644-9", "Fairly often"),
    FREQUENTLY(5, "LA6482-9", "Frequently");

    private final int score;
    private final String code;
    private final String text;

    ScreeningAnswer(int score, String code, String text) {
        this.score = score;
        this.code = code;
        this.text = text;
    }

    public int getScore() {
        return score;
    }

    public String getCode() {
        return code;
    }

    public String getText() {
        return text;
    }

    public static Map<String, Integer> getCodeToScoreMap() {
        return Stream.of(values())
                .collect(Collectors.toMap(ScreeningAnswer::getCode, ScreeningAnswer::getScore));
    }
}

