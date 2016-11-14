package com.oltpbenchmark.distributions;

import com.oltpbenchmark.distributions.Utils;

/**
 * An extension of the zipfian genarator that focuses the hotspots on the center of fractions of the
 * keyspace.   Centering is acheived by adding an centered offset to the zipfian value and treating odd/even
 * as +/- the center.
 * <p/>
 * Taken from https://github.com/treilly-nuodb/YCSB
 */
public class FocusedZipfianGenerator extends IntegerGenerator {
    ZipfianGenerator gen;
    long _min, _max, _itemcount, _offset;

    /**

     */
    public FocusedZipfianGenerator(long min, long max, long num, long denom) {
        _min = min;
        _max = max;
        _itemcount = _max - _min + 1;
        _offset = (_itemcount / (2 * denom)) * (num * 2 + 1);
        gen = new ZipfianGenerator(0, _itemcount);
    }

    /**************************************************************************************************/

    /**
     * Return the next int in the sequence.
     */
    @Override
    public int nextInt() {
        return (int) nextLong();
    }

    /**
     * Return the next long in the sequence.
     */
    public long nextLong() {
        long ret = gen.nextLong();

        // odd numbers are greater than center, even less
        if (ret % 2 == 0) {
            ret = -1 * ret;
        }

        // since we spread odd/even up/down we must divide by two to avoid holes.
        ret /= 2;
        ret = _min + (ret + _offset) % _itemcount;
        setLastInt((int) ret);
        return ret;
    }

    @Override
    public double mean() {
        return 0;
    }
}

