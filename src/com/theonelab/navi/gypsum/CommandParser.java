package com.theonelab.navi.gypsum;

import android.content.Context;
import android.util.Log;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.io.StreamTokenizer;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * A multi-threaded Gypsum protocol command parser.
 *
 * Parses incoming commands as documented in navi.org's Gypsum section and hands
 * off the resulting RPC calls to a set of {@link Command} instances for further
 * processing. Expected use is as follows:
 *
 * <pre>
 * {@code
 * CommandParser parser = new CommandParser();
 * Thread parserThread = new Thread(parser);
 * parserThread.start();
 *
 * while (String sexpr = stream.readLine()) {
 *   parserThread.addSexpr(sexpr);
 * }
 *
 * synchronized (parser) {
 *   parserThread.interrupt();
 *   parser.wait();
 * }
 * }
 * </pre>
 *
 * TODO(jtgans): Refactor this to take an InputStream directly instead of doing
 * this silly Queue business.
 */
public class CommandParser implements Runnable {
  private static final String TAG = "CommandParser";

  public interface Listener {
    /** Notifies when the {@link CommandParser} has stopped running. */
    public void onParserStopped(CommandParser parser);
  }

  /** Used to keep track of commands to run. */
  private final ConcurrentHashMap<String, Command> commands;

  /** Source for reading lines. */
  private final LineNumberReader reader;

  /** Listener for various {@link CommandParser} events. */
  private final Listener listener;

  /** Contains all Values for possible parameters. */
  private final ConcurrentHashMap<String, Value> params;

  /**
   * Constructs a new {@link CommandParser} instance from scratch with a null
   * parameters table.
   *
   * If the application is running in an Android emulated environment (as
   * determined by {@link AndroidHelper#isRunningOnEmulator}) commands will be
   * read without any compression enabled.
   */
  public CommandParser(Context context, InputStream stream, Listener listener) {
    commands = new ConcurrentHashMap<String, Command>();
    params = new ConcurrentHashMap<String, Value>();

    Log.i(TAG, "Expecting uncompressed input.");
    reader =
        new LineNumberReader(
            new InputStreamReader(stream));

    this.listener = listener;
  }

  /**
   * @return an immutable instance of the parameters table.
   */
  public Map<String, Value> getParams() {
    return Collections.unmodifiableMap(params);
  }

  /**
   * Clears out any parameters and resets everything back to zeroes.
   */
  public void clearParams() {
    params.clear();
  }

  /**
   * Registers a given {@link Command} with the processor.
   */
  public void registerCommand(String commandName, Command command) {
    commands.putIfAbsent(commandName, command);
  }

  /**
   * The main processing loop.
   *
   * This effectively polls for strings stuffed onto the sexprs queue and
   * parses them out. In doing so, it also dispatches calls to other processes
   * registered in the commands map.
   */
  @Override
  public void run() {
    try {
      String sexpr = null;
      
      while ((!Thread.interrupted()) && ((sexpr = reader.readLine()) != null)) {
        String commandName = parse(sexpr, params);

        if (commandName == null) {
          Log.e(TAG, "Couldn't parse sexpr '" + sexpr + "'");
          continue;
        }

        Command command = commands.get(commandName);

        if (command == null) {
          Log.w(TAG, "No command registered for [" + commandName + "].");
          continue;
        }

        command.execute(Collections.unmodifiableMap(params));
      }

      Log.i(TAG, "Exited runloop due to end-of-stream (normal exit).");
    } catch (IOException e) {
      Log.i(TAG, e + " while reading tokens from stream: " + e.getMessage());
    } finally {
      // TODO: Do we want to close this? This will likely close the underlying
      // BluetoothSocket as well.
      try {
        reader.close();
      } catch (IOException e) {
        Log.i(TAG, "IOException thrown while closing reader: " + e.getMessage());
      }

      listener.onParserStopped(this);
    }
  }

  /**
   * Does a number of checks against a sexpr to ensure that it is well formed.
   *
   * Checks done:
   *   - Ensure it starts with (
   *   - Ensure it ends with )
   *   - Ensure () are balanced
   *   - Ensure "" are balanced
   *
   * @param sexpr the s-expression to check for well-formedness.
   * @return true if the given sexpr is well formed.
   * @visiblefortesting
   */
  static boolean isWellFormed(String sexpr) {
    if (sexpr == null) {
      Log.wtf(TAG, "sexpr is (null)?!");
      return false;
    }

    if (!sexpr.startsWith("(")) {
      return false;
    }

    if (!sexpr.endsWith(")")) {
      return false;
    }

    // Ensure we have balanced parens -- count them!
    Pattern parenPattern = Pattern.compile("[\\)\\(]");
    Matcher parenMatcher = parenPattern.matcher(sexpr);
    int parenCount = 0;

    while (parenMatcher.find()) {
      if (parenMatcher.group(0).equals("(")) parenCount++;
      if (parenMatcher.group(0).equals(")")) parenCount--;
    }

    if (parenCount != 0) {
      Log.v(TAG, "sexpr parens unbalanced.");

      if (parenCount > 0) {
        Log.w(TAG, "sexpr parens unbalanced: too many (s");
      } else if (parenCount < 0) {
        Log.w(TAG, "sexpr parens unbalanced: too many )s");
      }

      return false;
    }

    // Ensure we have balanced quotes -- count them, too!
    Pattern quotePattern = Pattern.compile("\"");
    Matcher quoteMatcher = quotePattern.matcher(sexpr);
    int quoteCount = 0;

    while (quoteMatcher.find()) {
      quoteCount++;
    }

    if ((quoteCount % 2) != 0) {
      Log.w(TAG, "sexpr quotes unbalanced: too many/few \"s.");
      return false;
    }

    return true;
  }

  /**
   * Prepares a new {@link StreamTokenizer} for parsing the given sexpr.
   */
  private static StreamTokenizer getTokenizer(String sexpr) {
    StringReader reader = new StringReader(sexpr);
    StreamTokenizer tokenizer = new StreamTokenizer(reader);

    tokenizer.resetSyntax();

    tokenizer.eolIsSignificant(true);
    tokenizer.lowerCaseMode(true);
    tokenizer.parseNumbers();

    tokenizer.quoteChar('"');

    tokenizer.wordChars('a', 'z');
    tokenizer.wordChars('A', 'Z');
    tokenizer.wordChars('-', '-');

    // Allow for dots in dotted pairs.
    tokenizer.ordinaryChar('.');

    // 32 is space, everything before is control characters.
    tokenizer.whitespaceChars(0, 32);

    return tokenizer;
  }

  /**
   * Returns the command and extracts all tagged parameters in an s-expression.
   *
   * params is updated atomically, and only if the sexpr passed in was formatted
   * correctly.
   *
   * @param sexpr the s-expression to extract the tagged parameters from.
   * @param params the {@link Map} to store each {@link Paramenter} in.
   * @return a string containing the parsed command name, or null if the sexpr
   *         was invalid in some way.
   */
  public static String parse(String sexpr, Map<String, Value> params) {
    if (!isWellFormed(sexpr)) {
      Log.e(TAG, "Sexpr " + sexpr + " not well formed.");
      return null;
    }

    StreamTokenizer tokenizer = getTokenizer(sexpr);
    boolean inExpression = false;

    String command = null;
    int token;

    try {
      while ((token = tokenizer.nextToken()) != StreamTokenizer.TT_EOF) {
        switch (token) {
          case '(':
            if (inExpression) {
              Log.e(TAG, "Unexpected subexpression!");
              return null;
            }

            inExpression = true;
            break;

          case StreamTokenizer.TT_WORD:
            if (!inExpression) {
              Log.e(TAG, "Expression did not start with '('!");
              return null;
            } else if (command == null) {
              command = tokenizer.sval.toLowerCase();
            } else {
              Log.e(TAG, "Unrecognized bareword [" + tokenizer.sval + "]");
              return null;
            }

            break;

          case StreamTokenizer.TT_NUMBER:
            if (!inExpression) {
              Log.e(TAG, "Expression did not start with '('!");
              return null;
            }

            Log.e(TAG, "Unexpected number '" + tokenizer.nval + "'");
            return null;
          
          case '\'':
          case ':':
            if (!inExpression) {
              Log.e(TAG, "Expression did not start with '('!");
              return null;
            }

            if (command == null) {
              Log.e(TAG, "Premature start of plist -- no command found!");
              return null;
            }

            String paramName = CommandParser.parseParamSymbol(tokenizer);

            if (paramName == null) {
              Log.e(TAG, "Couldn't parse param name.");
              return null;
            }

            Value paramValue = null;

            if (params.containsKey(paramName)) {
              paramValue = params.get(paramName);
            } else {
              paramValue = new Value();
            }

            if (!CommandParser.parseParamValue(tokenizer, paramValue)) {
              Log.e(TAG, "Couldn't parse param name or value.");
              return null;
            }

            params.put(paramName, paramValue);
            break;

          case ')':
            if (!inExpression) {
              Log.e(TAG, "Premature end of expression -- did not start with '('!");
              return null;
            }

            if (command == null) {
              Log.e(TAG, "Premature end of expression -- no command specified!");
              return null;
            }

            inExpression = false;
            break;

          default:
            Log.v(TAG, "Tokenized unknown token [" + token + "].");
            Log.e(TAG, "Unexpected character '" + tokenizer.sval + "'!");
            return null;
        }
      }

      if (inExpression) {
        Log.e(TAG, "EOL encountered while waiting for end of expression!");
        return null;
      }

      return command;
    } catch (IOException e) {
      Log.e(TAG, "Caught IOException during parse -- this shouldn't happen!");
      return null;
    }
  }

  /**
   * Parses in a plist tag symbol.
   *
   * Parses a symbol of the form <code>:foo</code>. Extracts just the name part
   * from the symbol and returns it.
   */
  private static String parseParamSymbol(StreamTokenizer tokenizer)
      throws IOException {
    int token = tokenizer.nextToken();

    switch (token) {
      case StreamTokenizer.TT_WORD:
        return tokenizer.sval.toLowerCase();

      case StreamTokenizer.TT_EOF:
      case StreamTokenizer.TT_EOL:
        Log.e(TAG, "Unexpected end of stream in plist symbol!");
        return null;

      case StreamTokenizer.TT_NUMBER:
        Log.e(TAG, "Unexpected number '" + tokenizer.nval + " in plist symbol!");
        return null;

      default:
        Log.e(TAG, "Unexpected character '" + token + "' in plist symbol!");
        return null;
    }
  }

  /**
   * Parses in a plist value into a {@link Value} instance.
   *
   * Does some basic parsing of the various types and converts them into usable
   * {@link Value} instances.
   *
   * @param tokenizer The {@link StreamTokenizer} to grab tokens from.
   * @param value The previous {@link Value} -- this will be mutated on
   *              successful parsing.
   * @return true when the param value could be parsed, false otherwise. value
   *         remains unchanged on failure.
   */
  private static boolean parseParamValue(StreamTokenizer tokenizer, Value value)
      throws IOException {
    int token = tokenizer.nextToken();

    switch (token) {
      case StreamTokenizer.TT_EOF:
      case StreamTokenizer.TT_EOL:
        Log.e(TAG, "Unexpected end of stream in plist value!");
        return false;

      case StreamTokenizer.TT_WORD:
        String parsedValue = tokenizer.sval.toLowerCase();

        if (parsedValue.equals("nil")) {
          value.type = Value.Type.Boolean;
          value.bval = false;
          return true;
        } else if (parsedValue.equals("t")) {
          value.type = Value.Type.Boolean;
          value.bval = true;
          return true;
        }

        Log.e(TAG, "Unexpected bareword '" + tokenizer.sval + "' found in plist "
              + "value!");
        return false;

      case StreamTokenizer.TT_NUMBER:
        value.type = Value.Type.Number;
        value.ival = (float) tokenizer.nval;
        return true;

      case '"':    // Quoted string
        value.type = Value.Type.String;
        value.sval = tokenizer.sval;
        return true;

      case '\'':   // Symbol
        return parseSymbol(tokenizer, value);

      case '(':    // Coordinate
        return parseCoordinate(tokenizer, value);

      default:
        Log.e(TAG, "Unexpected character '" + token + "' in plist value!");
        return false;
    }
  }

  /**
   * Parses in a symbol expression.
   *
   * Symbol expressions are of the form <code>'foo</code>.
   *
   * @param tokenizer The {@link StreamTokenizer} to read tokens from.
   * @param value A {@link Value} to store the newly parsed value in. Will be
   *        mutated.
   * @return true if the symbol could be parsed successfully, false otherwise.
   *         value remains unchanged on failure.
   */
  private static boolean parseSymbol(StreamTokenizer tokenizer, Value value)
      throws IOException {
    int token = tokenizer.nextToken();

    if (token != StreamTokenizer.TT_WORD) {
      Log.e(TAG, "Malformed symbol found in plist!");
      return false;
    }

    value.type = Value.Type.Symbol;
    value.sval = tokenizer.sval;
    return true;
  }

  /**
   * Parses in a coordinate sub-sexpr.
   *
   * Coordinate sexprs are of the dotted cons cell form <code>(0 . 0)</code>.
   *
   * @param tokenizer The {@link StreamTokenizer} to read tokens from.
   * @param value A {@link Value} to store the newly parsed coordinate in. Will
   *        be mutated.
   * @return true if the coordinate could be parsed successfully, false
   *         otherwise. value remains unchanged on failure.
   */
  private static boolean parseCoordinate(StreamTokenizer tokenizer, Value value)
      throws IOException {
    int token = tokenizer.nextToken();

    if (token != StreamTokenizer.TT_NUMBER) {
      Log.e(TAG, "Expected number in sub-sexpr!");
      return false;
    }

    float x = (float) tokenizer.nval;
    token = tokenizer.nextToken();

    if (token != '.') {
      Log.e(TAG, "Expected dotted sub-sexpr!");
      return false;
    }

    token = tokenizer.nextToken();

    if (token != StreamTokenizer.TT_NUMBER) {
      Log.e(TAG, "Expected number in second half of sub-sexpr!");
      return false;
    }

    float y = (float) tokenizer.nval;
    token = tokenizer.nextToken();

    if (token != ')') {
      Log.e(TAG, "Expected end of sub-sexpr!");
      return false;
    }

    value.type = Value.Type.Coordinate;
    value.xcoord = x;
    value.ycoord = y;
    return true;
  }
}
