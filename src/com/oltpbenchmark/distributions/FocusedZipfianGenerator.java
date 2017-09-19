package com.oltpbenchmark.distributions;

import org.apache.log4j.Logger;

/**
 * An extension of the zipfian genarator that focuses the hotspots on the center of fractions of the
 * keyspace.   Centering is acheived by adding an centered offset to the zipfian value and treating odd/even
 * as +/- the center.
 * <p/>
 * Taken from https://github.com/treilly-nuodb/YCSB
 */
public class FocusedZipfianGenerator extends IntegerGenerator {
	private static final Logger LOG = Logger.getLogger(FocusedZipfianGenerator.class);
    ZipfianGenerator gen;
    long _min, _max, _itemcount, _offset;

    /**

     */
    public FocusedZipfianGenerator(long min, long max, long num, long denom) {
        /**
         * Only look at a fraction of the data set.
         */
        _min = min;
        _max = max;
        _itemcount = (_max - _min + 1) / denom;
        _offset = (_itemcount / 2) * (num * 2 + 1);
        String msg = String.format(
                "_min = %d, _max = %d, _itemcount = %d, _offset = %d",
                _min, _max, _itemcount, _offset);
        LOG.debug(msg);
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
        ret = ret + _offset;
        setLastInt((int) ret);
        return ret;
    }

    @Override
    public double mean() {
        return 0;
    }
}

