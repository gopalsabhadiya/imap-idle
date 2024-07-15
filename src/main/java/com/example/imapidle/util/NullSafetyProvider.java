package com.example.imapidle.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * https://stackoverflow.com/questions/4739947/what-is-the-memory-size-of-an-arraylist-in-java#:~:text=The%20minimum%20size%20for%20an,have%20100K%20elements%20or%20more.
 */
public class NullSafetyProvider {

    public static <T> List<T> arrayToList(T[] array) {
        if(array == null) {
            return new ArrayList<>();
        }
        return Arrays.asList(array);
    }
}
