package plc.homework;

import java.util.regex.Pattern;

/**
 * Contains {@link Pattern} constants, which are compiled regular expressions.
 * See the assignment page for resources on regexes as needed.
 */
public class Regex {

    public static final Pattern
            EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}"),

    EVEN_STRINGS = Pattern.compile("^.{2}(.{2}){4,9}$"),

    INTEGER_LIST = Pattern.compile("^\\[(\\d+(, ?\\d+)*)?\\]$"),

    NUMBER = Pattern.compile("^[+-]?\\d+(\\.\\d+)?$"),

    STRING = Pattern.compile("^\"(\\\\[bnrt'\"\\\\]|[^\"\\\\])*\"$");

}
