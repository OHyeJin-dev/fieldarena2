package com.agentsupport.healthanalysis;

import java.util.Map;
import java.util.Set;

public final class ChronicConditions {

  private ChronicConditions() {}

  public static final Map<String, String> CODES = Map.ofEntries(
      Map.entry("I10", "본태성 고혈압"),
      Map.entry("E11", "제2형 당뇨병"),
      Map.entry("I50", "심부전"),
      Map.entry("N18", "만성 신장병")
  );

  public static final Set<String> CHRONIC_CODE_SET = CODES.keySet();

  public static boolean isChronic(String code) {
    return CHRONIC_CODE_SET.contains(code);
  }
}
