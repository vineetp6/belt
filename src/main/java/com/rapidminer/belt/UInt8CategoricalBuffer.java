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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.rapidminer.belt.Column.Category;
import com.rapidminer.belt.util.IntegerFormats.Format;
import com.rapidminer.belt.util.IntegerFormats.PackedIntegers;


/**
 * Implementation of a {@link CategoricalColumnBuffer} with category index format {@link Format#UNSIGNED_INT8} that can
 * hold {@code 255} different categories. The category indices are stored as {@code byte}.
 *
 * @author Gisa Meier
 */
public class UInt8CategoricalBuffer<T> extends AbstractCategoricalColumnBuffer<T> {


	private final byte[] data;
	private boolean frozen = false;
	private final Map<T, Byte> indexLookup = new ConcurrentHashMap<>();
	private final List<T> valueLookup = new ArrayList<>();

	/**
	 * Creates a buffer of the given length.
	 *
	 * @param length
	 * 		the length of the buffer
	 * @throws IllegalArgumentException
	 * 		if the given length is negative
	 */
	public UInt8CategoricalBuffer(int length) {
		if (length < 0) {
			throw new IllegalArgumentException("Illegal Capacity: " + length);
		}
		data = new byte[length];
		valueLookup.add(null); //position 0 stands for missing value, i.e. null
	}

	/**
	 * Copies the data of the given column into a buffer. Throws an {@link UnsupportedOperationException} if the column
	 * category is not {@link Column.Category#CATEGORICAL} or if the compression format of the column has more values
	 * than supported by this buffer.
	 *
	 * @param column
	 * 		the column to convert to a buffer
	 * @param elementType
	 * 		the desired type of the buffer, must be a super type of the column type
	 */
	public UInt8CategoricalBuffer(Column column, Class<T> elementType) {
		if (column instanceof CategoricalColumn) {
			CategoricalColumn<?> categoricalColumn = (CategoricalColumn) column;
			if (categoricalColumn.getFormat() == indexFormat()) {
				//same format: directly copy the data
				byte[] originalData = categoricalColumn.getByteData().data();
				data = Arrays.copyOf(originalData, originalData.length);
			} else if (categoricalColumn.getFormat().maxValue() < indexFormat().maxValue()) {
				//smaller format: go via column reader
				data = new byte[column.size()];
				CategoricalColumnReader reader = new CategoricalColumnReader(column);
				for (int i = 0; i < data.length; i++) {
					data[i] = (byte) reader.read();
				}
			} else {
				//bigger format
				throw new UnsupportedOperationException("Column format incompatible with buffer format");
			}
		} else {
			throw new UnsupportedOperationException("Column is not categorical");
		}
		fillStructures(column.getDictionary(elementType));
	}

	private void fillStructures(List<T> values) {
		valueLookup.add(null);
		for (byte i = 1; i < values.size(); i++) {
			T value = values.get(i);
			valueLookup.add(value);
			indexLookup.put(value, i);
		}
	}

	@Override
	public T get(int index) {
		int valueIndex = Byte.toUnsignedInt(data[index]);
		synchronized (valueLookup) {
			return valueLookup.get(valueIndex);
		}
	}

	/**
	 * {@inheritDoc} This method is thread-safe.
	 */
	@Override
	public void set(int index, T value) {
		if (!setSave(index, value)) {
			throw new IllegalArgumentException("More than " + indexFormat().maxValue() + " different values.");
		}
	}

	/**
	 * {@inheritDoc} This method is thread-safe.
	 */
	@Override
	public boolean setSave(int index, T value) {
		if (frozen) {
			throw new IllegalStateException(BUFFER_FROZEN_MESSAGE);
		}
		if (value == null) {
			//set NaN
			data[index] = 0;
		} else {
			Byte mappingIndex = indexLookup.get(value);
			if (mappingIndex != null) {
				data[index] = mappingIndex;
			} else {
				byte newByteIndex;
				synchronized (valueLookup) {
					//double check that it was not added in parallel
					Byte mappingIndexAgain = indexLookup.get(value);
					if (mappingIndexAgain == null) {
						int newMappingIndex = valueLookup.size();
						if (newMappingIndex > indexFormat().maxValue()) {
							return false;
						}
						valueLookup.add(value);
						newByteIndex = (byte) newMappingIndex;
						indexLookup.put(value, newByteIndex);
					} else {
						newByteIndex = mappingIndexAgain;
					}
				}
				data[index] = newByteIndex;
			}
		}
		return true;
	}


	@Override
	public int size() {
		return data.length;
	}

	@Override
	public Format indexFormat() {
		return Format.UNSIGNED_INT8;
	}

	@Override
	public int differentValues() {
		synchronized (valueLookup) {
			return valueLookup.size() - 1;
		}
	}

	/**
	 * @return the underlying packed integers
	 */
	PackedIntegers getData() {
		return new PackedIntegers(data, indexFormat(), data.length);
	}


	@Override
	List<T> getMapping() {
		return valueLookup;
	}

	@Override
	void freeze() {
		frozen = true;
	}

	@Override
	public CategoricalColumn<T> toColumn(ColumnType<T> type) {
		Objects.requireNonNull(type, "Column type must not be null");
		if (type.category() != Category.CATEGORICAL) {
			throw new IllegalArgumentException("Column type must be categorical");
		}
		freeze();
		PackedIntegers bytes = new PackedIntegers(data, indexFormat(), data.length);
		return new SimpleCategoricalColumn<>(type, bytes, valueLookup);
	}
}