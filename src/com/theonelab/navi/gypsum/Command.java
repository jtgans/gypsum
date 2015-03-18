package com.theonelab.navi.gypsum;

import android.graphics.Color;
import android.util.Pair;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public abstract class Command {
  public static final Pair<Float, Float> ZERO_COORD = Pair.create(0f, 0f);

  public static String coordToString(Pair<Float, Float> coord) {
    return "(" + coord.first + " . " + coord.second + ")";
  }

  public static String getStringParam(String param, final Map<String, Value> params,
      String defValue) {
    if (params.containsKey(param)) {
      Value value = params.get(param);

      if ((value != null) && (value.type == Value.Type.String)) {
        return value.sval;
      }
    }

    return defValue;
  }

  public static String getSymbolParam(String param, final Map<String, Value> params,
      String defValue, String... otherVals) {
    if (params.containsKey(param)) {
      Value value = params.get(param);

      if ((value != null) && (value.type == Value.Type.Symbol)) {
        if ((otherVals != null) && (otherVals.length > 0)) {
          for (String otherVal : otherVals) {
            if (value.sval.equals(otherVal)) return value.sval;
          }
        }
      }
    }

    return defValue;
  }

  public static float getNumberParam(String param, final Map<String, Value> params,
      float defValue) {
    if (params.containsKey(param)) {
      Value value = params.get(param);

      if ((value != null) && (value.type == Value.Type.Number)) {
        return value.ival;
      }
    }

    return defValue;
  }

  public static Pair<Float, Float> getCoordParam(String param, final Map<String, Value> params) {
    return getCoordParam(param, params, ZERO_COORD);
  }

  public static Pair<Float, Float> getCoordParam(String param, final Map<String, Value> params,
      Pair<Float, Float> defValue) {
    if (params.containsKey(param)) {
      Value value = params.get(param);

      if ((value != null) && (value.type == Value.Type.Coordinate)) {
        return Pair.create(value.xcoord, value.ycoord);
      }
    }

    return defValue;
  }

  public static boolean getBooleanParam(String param, final Map<String, Value> params,
      boolean defValue) {
    if (params.containsKey(param)) {
      Value value = params.get(param);

      if ((value != null) && (value.type == Value.Type.Boolean)) {
        return value.bval;
      }
    }

    return defValue;
  }

  public static int getColorParam(String param, final Map<String, Value> params,
      int defValue) {
    if (params.containsKey(param)) {
      Value value = params.get(param);

      if ((value != null) && (value.type == Value.Type.String)) {
        return Color.parseColor(value.sval);
      }
    }

    return defValue;
  }

  public abstract void execute(final Map<String, Value> params);
}
