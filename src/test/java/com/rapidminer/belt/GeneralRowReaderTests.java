/**
 * This file is part of the RapidMiner Belt project.
 * Copyright (C) 2017-2018 RapidMiner GmbH
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see 
 * https://www.gnu.org/licenses/.
 */

package com.rapidminer.belt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;


/**
 * @author Gisa Meier
 * @see RowReaderTests
 */
@RunWith(Enclosed.class)
public class GeneralRowReaderTests {

	private static final double EPSILON = 1e-10;

	private static final int MAX_VALUES = 30;

	private static double[] randomNumbers(int n) {
		double[] numbers = new double[n];
		Arrays.setAll(numbers, i -> Math.random());
		return numbers;
	}

	private static int[] randomInts(int n) {
		int[] numbers = new int[n];
		Random random = new Random();
		Arrays.setAll(numbers, i -> random.nextInt(MAX_VALUES));
		return numbers;
	}

	private static List<String> getMappingList() {
		List<String> list = new ArrayList<>(MAX_VALUES);
		list.add(null);
		for (int i = 1; i < MAX_VALUES; i++) {
			list.add("value" + i);
		}
		return list;
	}

	private static void readAllColumns(GeneralRowReader reader) {
		while (reader.hasRemaining()) {
			reader.move();
			for (int j = 0; j < reader.width(); j++) {
				reader.getNumeric(j);
			}
		}
	}

	private static double[][] readAllColumnsToRealArrays(GeneralRowReader reader) {
		reader.setPosition(Row.BEFORE_FIRST);
		double[][] columns = new double[reader.width()][];
		Arrays.setAll(columns, i -> new double[reader.remaining()]);
		int i = 0;
		while (reader.hasRemaining()) {
			reader.move();
			for (int j = 0; j < reader.width(); j++) {
				columns[j][i] = reader.getNumeric(j);
			}
			i++;
		}
		return columns;
	}

	private static int[][] readAllColumnsToIndexArrays(GeneralRowReader reader) {
		reader.setPosition(Row.BEFORE_FIRST);
		int[][] columns = new int[reader.width()][];
		Arrays.setAll(columns, i -> new int[reader.remaining()]);
		int i = 0;
		while (reader.hasRemaining()) {
			reader.move();
			for (int j = 0; j < reader.width(); j++) {
				columns[j][i] = reader.getIndex(j);
			}
			i++;
		}
		return columns;
	}

	private static Object[][] readAllColumnsToObjectArrays(GeneralRowReader reader) {
		reader.setPosition(Row.BEFORE_FIRST);
		Object[][] columns = new Object[reader.width()][];
		Arrays.setAll(columns, i -> new Object[reader.remaining()]);
		int i = 0;
		while (reader.hasRemaining()) {
			reader.move();
			for (int j = 0; j < reader.width(); j++) {
				columns[j][i] = reader.getObject(j);
			}
			i++;
		}
		return columns;
	}

	private static double[] readColumnToNumeric(Column column) {
		double[] output = new double[column.size()];
		ColumnReader reader = new ColumnReader(column);
		for (int i = 0; i < output.length; i++) {
			output[i] = reader.read();
		}
		return output;
	}

	private static Object[] readColumnToObject(Column column) {
		Object[] output = new Object[column.size()];
		ObjectColumnReader<Object> reader = new ObjectColumnReader<>(column, Object.class);
		for (int i = 0; i < output.length; i++) {
			output[i] = reader.read();
		}
		return output;
	}


	public static class Reading {

		@Test
		public void testReadingDifferentCategories() {
			int nRows = 123;
			int nColumns = 3;

			int[] input1 = randomInts(nRows);
			double[] input2 = randomNumbers(nRows);
			input2[42] = Double.NaN;
			Object[] input3 = new Object[nRows];
			Arrays.setAll(input3, i -> "value" + i);
			input3[99] = null;
			List<String> mappingList = getMappingList();

			Column[] columns = new Column[]{
					new SimpleCategoricalColumn<>(ColumnTypes.NOMINAL, input1, mappingList),
					new DoubleArrayColumn(input2),
					new SimpleFreeColumn<>(ColumnTypes.freeType("test", String.class, null), input3)
			};

			GeneralRowReader reader = new GeneralRowReader(columns);
			double[][] outputs = readAllColumnsToRealArrays(reader);
			int[][] indexOutputs = readAllColumnsToIndexArrays(reader);
			Object[][] objectOutputs = readAllColumnsToObjectArrays(reader);

			assertEquals(nColumns, outputs.length);
			assertEquals(nColumns, indexOutputs.length);
			assertEquals(nColumns, objectOutputs.length);

			assertArrayEquals(input1, indexOutputs[0]);
			double[] output1 = new double[input1.length];
			Arrays.setAll(output1, i -> input1[i] == 0 ? Double.NaN : input1[i]);
			assertArrayEquals(output1, outputs[0], EPSILON);
			Object[] objectOutput1 = new Object[input1.length];
			Arrays.setAll(objectOutput1, i -> mappingList.get(input1[i]));
			assertArrayEquals(objectOutput1, objectOutputs[0]);

			assertArrayEquals(input2, outputs[1], EPSILON);
			assertArrayEquals(new Object[nRows], objectOutputs[1]);
			int[] output2 = new int[input2.length];
			Arrays.setAll(output2, i -> (int) input2[i]);
			assertArrayEquals(output2, indexOutputs[1]);

			assertArrayEquals(input3, objectOutputs[2]);
			assertArrayEquals(new int[nRows], indexOutputs[2]);
			double[] output3 = new double[input3.length];
			Arrays.fill(output3, Double.NaN);
			assertArrayEquals(output3, outputs[2], EPSILON);

		}

		@Test
		public void testReadingFromSingleCompleteBuffer() {
			int nRows = ColumnReader.SMALL_BUFFER_SIZE;
			int nColumns = 3;

			int[][] inputs = new int[nColumns][];
			Arrays.setAll(inputs, i -> randomInts(nRows));

			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new SimpleCategoricalColumn<>(ColumnTypes.NOMINAL, inputs[i], getMappingList()));

			GeneralRowReader reader = new GeneralRowReader(columns);
			double[][] outputs = readAllColumnsToRealArrays(reader);
			int[][] indexOutputs = readAllColumnsToIndexArrays(reader);
			Object[][] objectOutputs = readAllColumnsToObjectArrays(reader);

			assertEquals(nColumns, outputs.length);
			assertEquals(nColumns, indexOutputs.length);
			assertEquals(nColumns, objectOutputs.length);

			for (int i = 0; i < nColumns; i++) {
				assertArrayEquals(inputs[i], indexOutputs[i]);
				assertArrayEquals(readColumnToNumeric(columns[i]), outputs[i], EPSILON);
				assertArrayEquals(readColumnToObject(columns[i]), objectOutputs[i]);
			}

		}


		@Test
		public void testReadingFromSingleIncompleteBuffer() {
			int nRows = (int) (0.33 * ColumnReader.SMALL_BUFFER_SIZE);
			int nColumns = 5;

			int[][] inputs = new int[nColumns][];
			Arrays.setAll(inputs, i -> randomInts(nRows));

			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new SimpleCategoricalColumn<>(ColumnTypes.NOMINAL, inputs[i], getMappingList()));

			GeneralRowReader reader = new GeneralRowReader(columns);
			double[][] outputs = readAllColumnsToRealArrays(reader);
			int[][] indexOutputs = readAllColumnsToIndexArrays(reader);
			Object[][] objectOutputs = readAllColumnsToObjectArrays(reader);

			assertEquals(nColumns, outputs.length);
			assertEquals(nColumns, indexOutputs.length);
			assertEquals(nColumns, objectOutputs.length);

			for (int i = 0; i < nColumns; i++) {
				assertArrayEquals(inputs[i], indexOutputs[i]);
				assertArrayEquals(readColumnToNumeric(columns[i]), outputs[i], EPSILON);
				assertArrayEquals(readColumnToObject(columns[i]), objectOutputs[i]);
			}
		}

		@Test
		public void testReadingFromMultipleCompleteBuffers() {
			int nRows = 7 * ColumnReader.SMALL_BUFFER_SIZE;
			int nColumns = 3;

			int[][] inputs = new int[nColumns][];
			Arrays.setAll(inputs, i -> randomInts(nRows));

			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new SimpleCategoricalColumn<>(ColumnTypes.NOMINAL, inputs[i], getMappingList()));

			GeneralRowReader reader = new GeneralRowReader(columns);
			double[][] outputs = readAllColumnsToRealArrays(reader);
			int[][] indexOutputs = readAllColumnsToIndexArrays(reader);
			Object[][] objectOutputs = readAllColumnsToObjectArrays(reader);

			assertEquals(nColumns, outputs.length);
			assertEquals(nColumns, indexOutputs.length);
			assertEquals(nColumns, objectOutputs.length);

			for (int i = 0; i < nColumns; i++) {
				assertArrayEquals(inputs[i], indexOutputs[i]);
				assertArrayEquals(readColumnToNumeric(columns[i]), outputs[i], EPSILON);
				assertArrayEquals(readColumnToObject(columns[i]), objectOutputs[i]);
			}
		}

		@Test
		public void testReadingFromMultipleIncompleteBuffers() {
			int nRows = (int) (6.67 * ColumnReader.SMALL_BUFFER_SIZE);
			int nColumns = 3;

			int[][] inputs = new int[nColumns][];
			Arrays.setAll(inputs, i -> randomInts(nRows));

			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new SimpleCategoricalColumn<>(ColumnTypes.NOMINAL, inputs[i], getMappingList()));

			GeneralRowReader reader = new GeneralRowReader(columns);
			double[][] outputs = readAllColumnsToRealArrays(reader);
			int[][] indexOutputs = readAllColumnsToIndexArrays(reader);
			Object[][] objectOutputs = readAllColumnsToObjectArrays(reader);

			assertEquals(nColumns, outputs.length);
			assertEquals(nColumns, indexOutputs.length);
			assertEquals(nColumns, objectOutputs.length);

			for (int i = 0; i < nColumns; i++) {
				assertArrayEquals(inputs[i], indexOutputs[i]);
				assertArrayEquals(readColumnToNumeric(columns[i]), outputs[i], EPSILON);
				assertArrayEquals(readColumnToObject(columns[i]), objectOutputs[i]);
			}
		}


		@Test
		public void testReadingFromSingleColumn() {
			int nRows = (int) (6.67 * ColumnReader.SMALL_BUFFER_SIZE);

			int[] input = randomInts(nRows);
			Column[] columns = new Column[]{new SimpleCategoricalColumn<>(ColumnTypes.NOMINAL, input, getMappingList())};

			GeneralRowReader reader = new GeneralRowReader(columns);
			double[][] outputs = readAllColumnsToRealArrays(reader);
			int[][] indexOutputs = readAllColumnsToIndexArrays(reader);
			Object[][] objectOutputs = readAllColumnsToObjectArrays(reader);

			assertEquals(1, outputs.length);
			assertEquals(1, indexOutputs.length);
			assertEquals(1, objectOutputs.length);
			assertArrayEquals(readColumnToNumeric(columns[0]), outputs[0], EPSILON);
			assertArrayEquals(input, indexOutputs[0]);
			assertArrayEquals(readColumnToObject(columns[0]), objectOutputs[0]);
		}

		@Test
		public void testReadingFromTwoColumns() {
			int nRows = (int) (6.67 * ColumnReader.SMALL_BUFFER_SIZE);

			int[][] inputs = new int[3][];
			Arrays.setAll(inputs, i -> randomInts(nRows));
			String[] labels = new String[]{"a", "b", "c"};
			Column[] columns = new Column[3];
			Arrays.setAll(columns, i -> new SimpleCategoricalColumn<>(ColumnTypes.NOMINAL, inputs[i], getMappingList()));

			Table table = new Table(columns, labels);

			GeneralRowReader reader = new GeneralRowReader(table.column("c"), table.column("a"));
			double[][] outputs = readAllColumnsToRealArrays(reader);
			int[][] indexOutputs = readAllColumnsToIndexArrays(reader);
			Object[][] objectOutputs = readAllColumnsToObjectArrays(reader);

			assertEquals(2, outputs.length);
			assertEquals(2, indexOutputs.length);
			assertEquals(2, objectOutputs.length);
			assertArrayEquals(inputs[2], indexOutputs[0]);
			assertArrayEquals(inputs[0], indexOutputs[1]);
			assertArrayEquals(readColumnToNumeric(columns[2]), outputs[0], EPSILON);
			assertArrayEquals(readColumnToNumeric(columns[0]), outputs[1], EPSILON);
			assertArrayEquals(readColumnToObject(columns[2]), objectOutputs[0]);
			assertArrayEquals(readColumnToObject(columns[0]), objectOutputs[1]);
		}

		@Test
		public void testReadingFromTwoColumnTable() {
			int nRows = (int) (6.67 * ColumnReader.SMALL_BUFFER_SIZE);

			int[][] inputs = new int[2][];
			Arrays.setAll(inputs, i -> randomInts(nRows));
			String[] labels = new String[]{"a", "b"};
			Column[] columns = new Column[2];
			Arrays.setAll(columns, i -> new SimpleCategoricalColumn<>(ColumnTypes.NOMINAL, inputs[i], getMappingList()));

			Table table = new Table(columns, labels);

			GeneralRowReader reader = new GeneralRowReader(table);
			double[][] outputs = readAllColumnsToRealArrays(reader);
			int[][] indexOutputs = readAllColumnsToIndexArrays(reader);
			Object[][] objectOutputs = readAllColumnsToObjectArrays(reader);

			assertEquals(2, outputs.length);
			assertEquals(2, indexOutputs.length);
			assertEquals(2, objectOutputs.length);
			assertArrayEquals(inputs[0], indexOutputs[0]);
			assertArrayEquals(inputs[1], indexOutputs[1]);
			assertArrayEquals(readColumnToNumeric(columns[0]), outputs[0], EPSILON);
			assertArrayEquals(readColumnToNumeric(columns[1]), outputs[1], EPSILON);
			assertArrayEquals(readColumnToObject(columns[0]), objectOutputs[0]);
			assertArrayEquals(readColumnToObject(columns[1]), objectOutputs[1]);
		}

		@Test
		public void testReadingFromThreeColumns() {
			int nRows = (int) (6.67 * ColumnReader.SMALL_BUFFER_SIZE);

			int nColumns = 3;
			int[][] inputs = new int[nColumns][];
			Arrays.setAll(inputs, i -> randomInts(nRows));

			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new SimpleCategoricalColumn<>(ColumnTypes.NOMINAL, inputs[i], getMappingList()));

			GeneralRowReader reader = new GeneralRowReader(columns[0], columns[1], columns[2]);
			double[][] outputs = readAllColumnsToRealArrays(reader);
			int[][] indexOutputs = readAllColumnsToIndexArrays(reader);
			Object[][] objectOutputs = readAllColumnsToObjectArrays(reader);

			assertEquals(nColumns, outputs.length);
			assertEquals(nColumns, indexOutputs.length);
			assertEquals(nColumns, objectOutputs.length);

			for (int i = 0; i < nColumns; i++) {
				assertArrayEquals(inputs[i], indexOutputs[i]);
				assertArrayEquals(readColumnToNumeric(columns[i]), outputs[i], EPSILON);
				assertArrayEquals(readColumnToObject(columns[i]), objectOutputs[i]);
			}
		}

		@Test
		public void testColumnInteractionWithSingleBufferNumeric() {
			int nRows = ColumnReader.SMALL_BUFFER_SIZE;
			int nColumns = 5;

			double[][] inputs = new double[nColumns][];
			Arrays.setAll(inputs, i -> new double[nRows]);

			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> spy(new DoubleArrayColumn(inputs[i])));

			GeneralRowReader reader = new GeneralRowReader(columns);
			readAllColumns(reader);

			for (Column column : columns) {
				verify(column).fill(any(double[].class), eq(0), anyInt(), anyInt());
				verify(column, times(1)).fill(any(double[].class), anyInt(), anyInt(), anyInt());
				verify(column, times(0)).fill(any(Object[].class), anyInt(), anyInt(), anyInt());
			}
		}

		@Test
		public void testColumnInteractionWithSingleBufferCategorical() {
			int nRows = ColumnReader.SMALL_BUFFER_SIZE;
			int nColumns = 5;

			int[][] inputs = new int[nColumns][];
			Arrays.setAll(inputs, i -> randomInts(nRows));

			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> spy(new SimpleCategoricalColumn<>(ColumnTypes.NOMINAL, inputs[i], getMappingList())));

			GeneralRowReader reader = new GeneralRowReader(columns);
			readAllColumns(reader);

			for (Column column : columns) {
				verify(column).fill(any(double[].class), eq(0), anyInt(), anyInt());
				verify(column, times(1)).fill(any(double[].class), anyInt(), anyInt(), anyInt());
				verify(column).fill(any(Object[].class), eq(0), anyInt(), anyInt());
				verify(column, times(1)).fill(any(Object[].class), anyInt(), anyInt(), anyInt());
			}
		}

		@Test
		public void testColumnInteractionWithSingleBufferFree() {
			int nRows = ColumnReader.SMALL_BUFFER_SIZE;
			int nColumns = 5;

			Object[][] inputs = new Object[nColumns][];
			Arrays.setAll(inputs, i -> new Object[nRows]);

			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> spy(new SimpleFreeColumn<>(
					ColumnTypes.freeType("test", Double.class, null), inputs[i])));

			GeneralRowReader reader = new GeneralRowReader(columns);
			readAllColumns(reader);

			for (Column column : columns) {
				verify(column, times(0)).fill(any(double[].class), anyInt(), anyInt(), anyInt());
				verify(column).fill(any(Object[].class), eq(0), anyInt(), anyInt());
				verify(column, times(1)).fill(any(Object[].class), anyInt(), anyInt(), anyInt());
			}
		}

		@Test
		public void testColumnInteractionWithMultipleBufferNumeric() {
			int nRows = (int) (2.5 * ColumnReader.SMALL_BUFFER_SIZE);
			int nColumns = 3;

			double[][] inputs = new double[nColumns][];
			Arrays.setAll(inputs, i -> new double[nRows]);

			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> spy(new DoubleArrayColumn(inputs[i])));

			GeneralRowReader reader = new GeneralRowReader(columns);
			readAllColumns(reader);

			for (Column column : columns) {
				verify(column).fill(any(double[].class), eq(0), anyInt(), anyInt());
				verify(column).fill(any(double[].class), eq(ColumnReader.SMALL_BUFFER_SIZE), anyInt(), anyInt());
				verify(column).fill(any(double[].class), eq(2 * ColumnReader.SMALL_BUFFER_SIZE), anyInt(), anyInt());
				verify(column, times(3)).fill(any(double[].class), anyInt(), anyInt(), anyInt());
				verify(column, times(0)).fill(any(Object[].class), anyInt(), anyInt(), anyInt());
			}
		}

		@Test
		public void testColumnInteractionWithMultipleBufferCategorical() {
			int nRows = (int) (2.5 * ColumnReader.SMALL_BUFFER_SIZE);
			int nColumns = 3;

			int[][] inputs = new int[nColumns][];
			Arrays.setAll(inputs, i -> randomInts(nRows));

			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> spy(new SimpleCategoricalColumn<>(ColumnTypes.NOMINAL, inputs[i], getMappingList())));

			GeneralRowReader reader = new GeneralRowReader(columns);
			readAllColumns(reader);

			for (Column column : columns) {
				verify(column).fill(any(double[].class), eq(0), anyInt(), anyInt());
				verify(column).fill(any(double[].class), eq(ColumnReader.SMALL_BUFFER_SIZE), anyInt(), anyInt());
				verify(column).fill(any(double[].class), eq(2 * ColumnReader.SMALL_BUFFER_SIZE), anyInt(), anyInt());
				verify(column, times(3)).fill(any(double[].class), anyInt(), anyInt(), anyInt());
				verify(column).fill(any(Object[].class), eq(0), anyInt(), anyInt());
				verify(column).fill(any(Object[].class), eq(ColumnReader.SMALL_BUFFER_SIZE), anyInt(), anyInt());
				verify(column).fill(any(Object[].class), eq(2 * ColumnReader.SMALL_BUFFER_SIZE), anyInt(), anyInt());
				verify(column, times(3)).fill(any(Object[].class), anyInt(), anyInt(), anyInt());
			}
		}

		@Test
		public void testColumnInteractionWithMultipleBufferFree() {
			int nRows = (int) (2.5 * ColumnReader.SMALL_BUFFER_SIZE);
			int nColumns = 3;

			Object[][] inputs = new Object[nColumns][];
			Arrays.setAll(inputs, i -> new Object[nRows]);

			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> spy(new SimpleFreeColumn<>(
					ColumnTypes.freeType("test", Double.class, null), inputs[i])));

			GeneralRowReader reader = new GeneralRowReader(columns);
			readAllColumns(reader);

			for (Column column : columns) {
				verify(column, times(0)).fill(any(double[].class), anyInt(), anyInt(), anyInt());
				verify(column).fill(any(Object[].class), eq(0), anyInt(), anyInt());
				verify(column).fill(any(Object[].class), eq(ColumnReader.SMALL_BUFFER_SIZE), anyInt(), anyInt());
				verify(column).fill(any(Object[].class), eq(2 * ColumnReader.SMALL_BUFFER_SIZE), anyInt(), anyInt());
				verify(column, times(3)).fill(any(Object[].class), anyInt(), anyInt(), anyInt());
			}
		}
	}

	public static class Remaining {

		@Test(expected = NullPointerException.class)
		public void testNullSource() {
			new GeneralRowReader(null, ColumnReader.SMALL_BUFFER_SIZE);
		}

		@Test
		public void testInitialRemainder() {
			int nRows = 64;
			int nColumns = 3;

			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new DoubleArrayColumn(new double[nRows]));

			GeneralRowReader reader = new GeneralRowReader(columns);
			assertEquals(nRows, reader.remaining());
		}

		@Test
		public void testRemainder() {
			int nRows = 64;
			int nColumns = 4;
			int nReads = 16;

			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new DoubleArrayColumn(new double[nRows]));

			GeneralRowReader reader = new GeneralRowReader(columns);
			for (int i = 0; i < nReads; i++) {
				reader.move();
			}

			assertEquals(nRows - nReads, reader.remaining());
		}

		@Test
		public void testFinalRemainder() {
			int nRows = 64;
			int nColumns = 11;

			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new DoubleArrayColumn(new double[nRows]));

			GeneralRowReader reader = new GeneralRowReader(columns);
			while (reader.hasRemaining()) {
				reader.move();
			}

			assertEquals(0, reader.remaining());
		}

		@Test
		public void testEmptyArray() {
			GeneralRowReader reader = new GeneralRowReader(new Column[0]);
			assertEquals(0, reader.width());
			assertEquals(0, reader.remaining());
		}
	}

	public static class Position {

		@Test
		public void testGet() {
			int nRows = 64;
			int nColumns = 4;
			int nReads = 16;

			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new DoubleArrayColumn(new double[nRows]));

			GeneralRowReader reader = new GeneralRowReader(columns);
			for (int i = 0; i < nReads; i++) {
				reader.move();
			}

			assertEquals(nReads - 1, reader.position());
		}

		@Test
		public void testGetBufferSmaller() {
			int nRows = 64;
			int nColumns = 4;
			int nReads = 16;

			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new DoubleArrayColumn(new double[nRows]));

			GeneralRowReader reader = new GeneralRowReader(columns, 10 * nColumns);
			for (int i = 0; i < nReads; i++) {
				reader.move();
			}

			assertEquals(nReads - 1, reader.position());
		}

		@Test
		public void testGetAtStart() {
			int nRows = 64;
			int nColumns = 3;

			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new DoubleArrayColumn(new double[nRows]));

			GeneralRowReader reader = new GeneralRowReader(columns);

			assertEquals(Row.BEFORE_FIRST, reader.position());
		}

		@Test
		public void testGetAtEnd() {
			int nRows = 64;
			int nColumns = 3;

			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new DoubleArrayColumn(new double[nRows]));
			GeneralRowReader reader = new GeneralRowReader(columns);
			while (reader.hasRemaining()) {
				reader.move();
			}
			assertEquals(nRows - 1, reader.position());
		}

		@Test
		public void testGetAtEndBufferSmaller() {
			int nRows = 64;
			int nColumns = 3;

			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new DoubleArrayColumn(new double[nRows]));
			GeneralRowReader reader = new GeneralRowReader(columns, 10 * nColumns);
			while (reader.hasRemaining()) {
				reader.move();
			}
			assertEquals(nRows - 1, reader.position());
		}

		@Test
		public void testSet() {
			int nRows = 64;
			int nColumns = 3;
			int position = 16;
			double[] testArray = new double[nRows];
			Arrays.setAll(testArray, i -> i);
			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new DoubleArrayColumn(testArray));
			GeneralRowReader reader = new GeneralRowReader(columns, 10 * nColumns);
			reader.setPosition(position);
			assertEquals(position, reader.position());
			reader.move();
			for (int i = 0; i < reader.width(); i++) {
				assertEquals(position + 1, reader.getNumeric(i), EPSILON);
			}
			reader.move();
			for (int i = 0; i < reader.width(); i++) {
				assertEquals(position + 2, reader.getNumeric(0), EPSILON);
			}
		}

		@Test
		public void testSetAfterMoves() {
			int nRows = 64;
			int nColumns = 3;
			int position = 16;
			double[] testArray = new double[nRows];
			Arrays.setAll(testArray, i -> i);
			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new DoubleArrayColumn(testArray));
			GeneralRowReader reader = new GeneralRowReader(columns, 10 * nColumns);
			for (int i = 0; i < 13; i++) {
				reader.move();
			}
			reader.setPosition(position);
			assertEquals(position, reader.position());
			reader.move();
			for (int i = 0; i < reader.width(); i++) {
				assertEquals(position + 1, reader.getNumeric(i), EPSILON);
			}
			reader.move();
			for (int i = 0; i < reader.width(); i++) {
				assertEquals(position + 2, reader.getNumeric(0), EPSILON);
			}
		}

		@Test
		public void testSetAfterReadsSmallerInSameBuffer() {
			int nRows = 64;
			int nColumns = 3;
			int position = 11;
			double[] testArray = new double[nRows];
			Arrays.setAll(testArray, i -> i);
			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new DoubleArrayColumn(testArray));
			GeneralRowReader reader = new GeneralRowReader(columns, 10 * nColumns);
			for (int i = 0; i < 13; i++) {
				reader.move();
			}
			reader.setPosition(position);
			assertEquals(position, reader.position());
			reader.move();
			for (int i = 0; i < reader.width(); i++) {
				assertEquals(position + 1, reader.getNumeric(i), EPSILON);
			}
			reader.move();
			for (int i = 0; i < reader.width(); i++) {
				assertEquals(position + 2, reader.getNumeric(0), EPSILON);
			}
		}

		@Test
		public void testSetAfterReadsSmaller() {
			int nRows = 64;
			int nColumns = 3;
			int position = 4;
			double[] testArray = new double[nRows];
			Arrays.setAll(testArray, i -> i);
			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new DoubleArrayColumn(testArray));
			GeneralRowReader reader = new GeneralRowReader(columns, 10 * nColumns);
			for (int i = 0; i < 13; i++) {
				reader.move();
			}
			reader.setPosition(position);
			assertEquals(position, reader.position());
			reader.move();
			for (int i = 0; i < reader.width(); i++) {
				assertEquals(position + 1, reader.getNumeric(i), EPSILON);
			}
			reader.move();
			for (int i = 0; i < reader.width(); i++) {
				assertEquals(position + 2, reader.getNumeric(0), EPSILON);
			}
		}

		@Test
		public void testSetBefore() {
			int nRows = 64;
			int nColumns = 3;
			double[] testArray = new double[nRows];
			Arrays.setAll(testArray, i -> i);
			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new DoubleArrayColumn(testArray));
			GeneralRowReader reader = new GeneralRowReader(columns, 10 * nColumns);
			reader.setPosition(Row.BEFORE_FIRST);
			assertEquals(-1, reader.position());
			reader.move();
			for (int i = 0; i < reader.width(); i++) {
				assertEquals(0, reader.getNumeric(0), EPSILON);
			}
			reader.move();
			for (int i = 0; i < reader.width(); i++) {
				assertEquals(1, reader.getNumeric(0), EPSILON);
			}
		}

		@Test
		public void testSetZero() {
			int nRows = 64;
			int nColumns = 3;
			double[] testArray = new double[nRows];
			Arrays.setAll(testArray, i -> i);
			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new DoubleArrayColumn(testArray));
			GeneralRowReader reader = new GeneralRowReader(columns, 10 * nColumns);
			reader.setPosition(0);
			assertEquals(0, reader.position());
			reader.move();
			for (int i = 0; i < reader.width(); i++) {
				assertEquals(1, reader.getNumeric(0), EPSILON);
			}
			reader.move();
			for (int i = 0; i < reader.width(); i++) {
				assertEquals(2, reader.getNumeric(0), EPSILON);
			}
		}

		@Test(expected = IndexOutOfBoundsException.class)
		public void testSetNegative() {
			int nRows = 64;
			int nColumns = 3;
			double[] testArray = new double[nRows];
			Arrays.setAll(testArray, i -> i);
			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new DoubleArrayColumn(testArray));
			GeneralRowReader reader = new GeneralRowReader(columns, 10 * nColumns);
			reader.setPosition(0);
			reader.move();
			reader.setPosition(-5);
			reader.move();
		}

		@Test
		public void testSetBeforeAfterMove() {
			int nRows = 64;
			int nColumns = 3;
			double[] testArray = new double[nRows];
			Arrays.setAll(testArray, i -> i);
			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new DoubleArrayColumn(testArray));
			GeneralRowReader reader = new GeneralRowReader(columns, 10 * nColumns);
			reader.move();
			reader.move();
			reader.setPosition(-1);
			assertEquals(-1, reader.position());
			reader.move();
			for (int i = 0; i < reader.width(); i++) {
				assertEquals(0, reader.getNumeric(0), EPSILON);
			}
			reader.move();
			for (int i = 0; i < reader.width(); i++) {
				assertEquals(1, reader.getNumeric(0), EPSILON);
			}
		}

		@Test
		public void testSetEnd() {
			int nRows = 64;
			int nColumns = 3;
			double[] testArray = new double[nRows];
			Arrays.setAll(testArray, i -> i);
			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new DoubleArrayColumn(testArray));
			GeneralRowReader reader = new GeneralRowReader(columns, 10 * nColumns);
			reader.setPosition(nRows - 2);
			reader.move();
			for (int i = 0; i < reader.width(); i++) {
				assertEquals(nRows - 1, reader.getNumeric(0), EPSILON);
			}
			assertEquals(nRows - 1, reader.position());
		}

		@Test
		public void testSetEndAfterMove() {
			int nRows = 64;
			int nColumns = 3;
			double[] testArray = new double[nRows];
			Arrays.setAll(testArray, i -> i);
			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new DoubleArrayColumn(testArray));
			GeneralRowReader reader = new GeneralRowReader(columns, 10 * nColumns);
			reader.move();
			reader.move();
			reader.setPosition(nRows - 2);
			reader.move();
			for (int i = 0; i < reader.width(); i++) {
				assertEquals(nRows - 1, reader.getNumeric(0), EPSILON);
			}
			assertEquals(nRows - 1, reader.position());
		}

		@Test
		public void testSetMultipleTimes() {
			int nRows = 64;
			int nColumns = 3;
			double[] testArray = new double[nRows];
			Arrays.setAll(testArray, i -> i);
			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new DoubleArrayColumn(testArray));
			GeneralRowReader reader = new GeneralRowReader(columns, 10 * nColumns);
			reader.setPosition(16);
			assertEquals(16, reader.position());
			reader.move();
			for (int i = 0; i < reader.width(); i++) {
				assertEquals(17, reader.getNumeric(0), EPSILON);
			}
			reader.setPosition(18);
			assertEquals(18, reader.position());
			reader.move();
			for (int i = 0; i < reader.width(); i++) {
				assertEquals(19, reader.getNumeric(0), EPSILON);
			}
			reader.setPosition(11);
			assertEquals(11, reader.position());
			reader.move();
			for (int i = 0; i < reader.width(); i++) {
				assertEquals(12, reader.getNumeric(0), EPSILON);
			}
			reader.setPosition(11);
			assertEquals(11, reader.position());
			reader.move();
			for (int i = 0; i < reader.width(); i++) {
				assertEquals(12, reader.getNumeric(0), EPSILON);
			}
			reader.setPosition(25);
			assertEquals(25, reader.position());
			reader.move();
			for (int i = 0; i < reader.width(); i++) {
				assertEquals(26, reader.getNumeric(0), EPSILON);
			}
			reader.setPosition(23);
			assertEquals(23, reader.position());
			reader.move();
			for (int i = 0; i < reader.width(); i++) {
				assertEquals(24, reader.getNumeric(0), EPSILON);
			}
		}
	}

	public static class ToString {

		@Test
		public void testStart() {
			int nRows = 64;
			int nColumns = 3;
			double[] testArray = new double[nRows];
			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new DoubleArrayColumn(testArray));
			GeneralRowReader reader = new GeneralRowReader(columns, 10 * nColumns);
			String expected = "General Row reader (" + nRows + "x" + nColumns + ")\n" + "Row position: " + Row.BEFORE_FIRST;
			assertEquals(expected, reader.toString());
		}

		@Test
		public void testMiddle() {
			int nRows = 64;
			int nColumns = 3;
			double[] testArray = new double[nRows];
			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new DoubleArrayColumn(testArray));
			GeneralRowReader reader = new GeneralRowReader(columns, 10 * nColumns);
			reader.move();
			reader.move();
			reader.move();
			reader.move();
			reader.move();
			String expected = "General Row reader (" + nRows + "x" + nColumns + ")\n" + "Row position: " + 4;
			assertEquals(expected, reader.toString());
		}

		@Test
		public void testEnd() {
			int nRows = 64;
			int nColumns = 3;
			double[] testArray = new double[nRows];
			Column[] columns = new Column[nColumns];
			Arrays.setAll(columns, i -> new DoubleArrayColumn(testArray));
			GeneralRowReader reader = new GeneralRowReader(columns, 10 * nColumns);
			while (reader.hasRemaining()) {
				reader.move();
			}
			String expected = "General Row reader (" + nRows + "x" + nColumns + ")\n" + "Row position: " + (nRows - 1);
			assertEquals(expected, reader.toString());
		}
	}
}
