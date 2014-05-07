package sample;

public class Algorithms {

	/**
	 * Searches the specified array of longs for the specified value using the binary search algorithm. The array must
	 * be sorted (as by the {@link #sort(long[])} method) prior to making this call. If it is not sorted, the results
	 * are undefined. If the array contains multiple elements with the specified value, there is no guarantee which one
	 * will be found.
	 *
	 * @param a
	 *            the array to be searched
	 * @param key
	 *            the value to be searched for
	 * @return index of the search key, if it is contained in the array; otherwise,
	 *         <tt>(-(<i>insertion point</i>) - 1)</tt>. The <i>insertion point</i> is defined as the point at which the
	 *         key would be inserted into the array: the index of the first element greater than the key, or
	 *         <tt>a.length</tt> if all elements in the array are less than the specified key. Note that this guarantees
	 *         that the return value will be &gt;= 0 if and only if the key is found.
	 */
	public static int binarySearch(long[] a, long key) {
		int low = 0;
		int high = a.length; // BUG: ' - 1' removed

		while (low <= high) {
			int mid = (low + high) / 2;
			long midVal = a[mid];

			if (midVal < key)
				low = mid + 1;
			else if (midVal > key)
				high = mid - 1;
			else
				return mid;
		}
		return -(low + 1);
	}

}
