package sample;

import java.util.concurrent.Callable;

public class AlgorithmsTest {

	// test cases

	public void testEmpty() {
		long[] input = new long[0];
		long actual = Algorithms.binarySearch(input, 1);
		assertEquals(-1, actual);
	}

	public void testHigh() {
		long[] input = new long[] { 1, 3 };
		long actual = Algorithms.binarySearch(input, 3);
		assertEquals(1, actual);
	}

	public void testLow() {
		long[] input = new long[] { 1, 2, 3, 4 };
		long actual = Algorithms.binarySearch(input, 2);
		assertEquals(1, actual);
	}

	// minimal test driver

	static void assertEquals(long expected, long actual) {
		if (expected != actual)
			throw new AssertionError("expected " + expected + " does not match actual " + actual);
	}

	public static void main(String[] args) {
		final AlgorithmsTest test = new AlgorithmsTest();
		runTest("testEmtpy", new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				test.testEmpty();
				return null;
			}
		});
		runTest("testHigh", new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				test.testHigh();
				return null;
			}
		});
		runTest("testLow", new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				test.testLow();
				return null;
			}
		});
	}

	static void runTest(String name, Callable<Void> test) {
		try {
			test.call();
			System.out.println(name + " passed");
		} catch (AssertionError e) {
			System.out.println(name + " failed");
			e.printStackTrace(System.out);
		} catch (Throwable e) {
			System.out.println(name + " errored");
			e.printStackTrace(System.out);
		}
	}

}
