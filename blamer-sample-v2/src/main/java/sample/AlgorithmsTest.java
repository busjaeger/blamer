package sample;

import static sample.Algorithms.binarySearch;

import java.util.concurrent.Callable;

public class AlgorithmsTest {

	// test cases

	void testEmpty() {
		long[] input = {};
		long actual = binarySearch(input, 1);
		assertEquals(-1, actual);
	}

	void testLow() {
		long[] input = { 1, 2, 3, 4 };
		long actual = binarySearch(input, 1);
		assertEquals(0, actual);
	}

	void testMid() {
		long[] input = { 1, 2, 3, 4 };
		long actual = binarySearch(input, 2);
		assertEquals(1, actual);
	}

	void testHigh() {
		long[] input = { 1, 2, 3, 4 };
		long actual = binarySearch(input, 3);
		assertEquals(2, actual);
	}

	// minimal test driver

	static void assertEquals(long expected, long actual) {
		if (expected != actual)
			throw new AssertionError("expected " + expected + " does not match actual " + actual);
	}

	public static void main(String[] args) {
		final AlgorithmsTest test = new AlgorithmsTest();
//		runTest("testEmtpy", new Callable<Void>() {
//			@Override
//			public Void call() throws Exception {
//				test.testEmpty();
//				return null;
//			}
//		});
		runTest("testLow", new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				test.testLow();
				return null;
			}
		});
//		runTest("testMid", new Callable<Void>() {
//			@Override
//			public Void call() throws Exception {
//				test.testMid();
//				return null;
//			}
//		});
//		runTest("testHigh", new Callable<Void>() {
//			@Override
//			public Void call() throws Exception {
//				test.testHigh();
//				return null;
//			}
//		});
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
