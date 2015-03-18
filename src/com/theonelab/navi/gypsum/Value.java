package com.theonelab.navi.gypsum;

/**
 * Simple varadic type to represent an S-expression plist value.
 *
 * Does not store a reference to its plist name. Note that the actual value
 * for types defined as {@link Value.String} and {@link Value.Symbol} is
 * stored in sval.
 */
public final class Value {
  /**
   * One of the expected data types used in {@link Value}.
   */
  public static enum Type {
    String,
    Number,
    Coordinate,
    Symbol,
    Boolean,
  };

  public Type type;

  public String sval;
  public float ival;
  public boolean bval;
  public float xcoord;
  public float ycoord;

  public Value() {
  }

  public Value(String sval, boolean isSymbol) {
    if (isSymbol) {
      type = Type.Symbol;
    } else {
      type = Type.String;
    }

    this.sval = sval;
  }

  public Value(float ival) {
    type = Type.Number;
    this.ival = ival;
  }

  public Value(float xcoord, float ycoord) {
    type = Type.Coordinate;
    this.xcoord = xcoord;
    this.ycoord = ycoord;
  }

  public Value(boolean bval) {
    type = Type.Boolean;
    this.bval = bval;
  }

  public String toString() {
    switch (type) {
      case Symbol:
        return "'" + sval;
      case String:
        return "\"" + sval + "\"";
      case Number:
        return String.valueOf(ival);
      case Coordinate:
        return "(" + xcoord + " . " + ycoord + ")";
      case Boolean:
        return String.valueOf(bval);
      default:
        return "#<unknown>";
    }
  }

  public boolean equals(Object o) {
    if (!(o instanceof Value)) return false;
    if (o == this) return true;

    Value v = (Value) o;
    if (type == v.type) {
      switch (type) {
        case Symbol:
        case String:
          return sval.equals(v.sval);
        case Number:
          return (ival == v.ival);
        case Coordinate:
          return ((xcoord == v.xcoord) && (ycoord == v.ycoord));
        case Boolean:
          return (bval == v.bval);
        default:
          return false;
      }
    }

    return false;
  }
}
