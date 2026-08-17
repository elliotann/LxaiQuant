package com.chain.ai.trade.engine.xchange.utils;



import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InputChecker {

  private static final String regEx = "[ _`~!@#$%^&*()+=|{}':;',\\[\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]|\n|\t";

  public static final String ALL_STR = "*";

  private static final InputChecker checkerInst;

  static {
    checkerInst = new InputChecker();
  }

  private InputChecker(){}

  public static InputChecker checker() {
    return checkerInst;
  }

  private boolean isSpecialChar(String str) {

    Pattern p = Pattern.compile(regEx);
    Matcher m = p.matcher(str);
    return m.find();
  }

  public <T> InputChecker shouldNotNull(T value, String name) {
    if (value == null) {
      throw new RuntimeException(
          "[Input] " + name + " should not be null");
    }
    return checkerInst;
  }

  public <T> InputChecker shouldNull(T value, String name) {
    if (value != null) {
      throw new RuntimeException(
          "[Input] " + name + " should be null");
    }
    return checkerInst;
  }

  public InputChecker checkSymbol(String symbol) {
    if (symbol == null || "".equals(symbol)) {
      throw new RuntimeException(
          "[Input] Symbol is mandatory");
    }

    if (ALL_STR.equals(symbol)) {
      return checkerInst;
    }

    if (isSpecialChar(symbol)) {
      throw new RuntimeException(
          "[Input] " + symbol + " is invalid symbol");
    }
    return checkerInst;
  }

  public InputChecker checkCurrency(String currency) {
    if (currency == null || "".equals(currency)) {
      throw new RuntimeException(
          "[Input] Currency is mandatory");
    }
    if (isSpecialChar(currency)) {
      throw new RuntimeException(
          "[Input] " + currency + " is invalid currency");
    }
    return checkerInst;
  }

  public InputChecker checkETF(String symbol) {
    if (!"hb10".equals(symbol)) {
      throw new RuntimeException(
          "currently only support hb10 :-)");
    }
    return checkerInst;
  }

  public  InputChecker checkRange(int size, int min, int max, String name) {
    if (!(min <= size && size <= max)) {
      throw new RuntimeException(
          "[Input] " + name + " is out of bound. " + size + " is not in [" + min + "," + max + "]");
    }
    return checkerInst;
  }

  public InputChecker checkSymbolList(List<String> symbols) {
    if (symbols == null || symbols.size() == 0) {
      throw new RuntimeException( "[Input] Symbol is mandatory");
    }
    for (String symbol : symbols) {
      checkSymbol(symbol);
    }
    return checkerInst;
  }

  public InputChecker checkRange(Integer size, int min, int max, String name) {
    if (size != null) {
      checkRange(size.intValue(), min, max, name);
    }
    return checkerInst;
  }

  public InputChecker greaterOrEqual(Integer value, int base, String name) {
    if (value != null && value < base) {
      throw new RuntimeException(
          "[Input] " + name + " should be greater than " + base);
    }
    return checkerInst;
  }



  public <T> InputChecker checkList(List<T> list, int min, int max, String name) {
    if (list != null) {
      if (list.size() > max) {
        throw new RuntimeException(
            "[Input] " + name + " is out of bound, the max size is " + max);
      } else if (list.size() < min) {
        throw new RuntimeException(
            "[Input] " + name + " should contain " + min + " item(s) at least");
      }
    }
    return checkerInst;
  }
}
