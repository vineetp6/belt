/**
 * This file is part of the RapidMiner Belt project.
 * Copyright (C) 2017-2019 RapidMiner GmbH
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

package com.rapidminer.belt.transform;


import java.util.function.Function;

import com.rapidminer.belt.buffer.CategoricalBuffer;
import com.rapidminer.belt.buffer.Int32CategoricalBuffer;
import com.rapidminer.belt.buffer.UInt16CategoricalBuffer;
import com.rapidminer.belt.buffer.UInt8CategoricalBuffer;
import com.rapidminer.belt.column.Column;
import com.rapidminer.belt.reader.ObjectReader;
import com.rapidminer.belt.reader.Readers;
import com.rapidminer.belt.util.IntegerFormats;


/**
 * Maps a {@link Column.Capability#OBJECT_READABLE} {@link Column} to a {@link CategoricalBuffer} using a given
 * mapping operator.
 *
 * @author Gisa Meier
 */
final class ApplierObjectToCategorical<R, T> implements Calculator<CategoricalBuffer<T>> {


	private CategoricalBuffer<T> target;
	private final Column source;
	private final Class<R> sourceType;
	private final Function<R, T> operator;
	private final IntegerFormats.Format format;

	ApplierObjectToCategorical(Column source, Class<R> sourceType, Function<R, T> operator, IntegerFormats.Format format) {
		this.source = source;
		this.operator = operator;
		this.sourceType = sourceType;
		this.format = format;
	}


	@Override
	public void init(int numberOfBatches) {
		switch (format) {
			case UNSIGNED_INT2:
			case UNSIGNED_INT4:
			case UNSIGNED_INT8:
				target = BufferAccessor.get().newUInt8Buffer(source.size(), format);
				break;
			case UNSIGNED_INT16:
				target = BufferAccessor.get().newUInt16Buffer(source.size());
				break;
			case SIGNED_INT32:
			default:
				target = BufferAccessor.get().newInt32Buffer(source.size());
		}
	}

	@Override
	public int getNumberOfOperations() {
		return source.size();
	}

	@Override
	public void doPart(int from, int to, int batchIndex) {
		switch (format) {
			case UNSIGNED_INT2:
			case UNSIGNED_INT4:
			case UNSIGNED_INT8:
				mapPart(source, sourceType, operator, (UInt8CategoricalBuffer<T>) target, from, to);
				break;
			case UNSIGNED_INT16:
				mapPart(source, sourceType, operator, (UInt16CategoricalBuffer<T>) target, from, to);
				break;
			case SIGNED_INT32:
			default:
				mapPart(source, sourceType, operator, (Int32CategoricalBuffer<T>) target, from, to);
		}
	}

	@Override
	public CategoricalBuffer<T> getResult() {
		return target;
	}

	/**
	 * Maps every index between from (inclusive) and to (exclusive) of the source column using the operator and stores
	 * the result in target in format {@link IntegerFormats.Format#UNSIGNED_INT2}.
	 */
	private static <R, T> void mapPart(Column source, Class<R> sourceType, Function<R, T> operator,
									   Int32CategoricalBuffer<T> target, int from, int to) {
		final ObjectReader<R> reader = Readers.objectReader(source, sourceType, to);
		reader.setPosition(from - 1);
		for (int i = from; i < to; i++) {
			R value = reader.read();
			target.set(i, operator.apply(value));
		}
	}

	/**
	 * Maps every index between from (inclusive) and to (exclusive) of the source column using the operator and stores
	 * the result in target in format {@link IntegerFormats.Format#UNSIGNED_INT4}.
	 */
	private static <R, T> void mapPart(Column source, Class<R> sourceType, Function<R, T> operator,
									   UInt16CategoricalBuffer<T> target, int from, int to) {
		final ObjectReader<R> reader = Readers.objectReader(source, sourceType, to);
		reader.setPosition(from - 1);
		for (int i = from; i < to; i++) {
			R value = reader.read();
			target.set(i, operator.apply(value));
		}
	}

	/**
	 * Maps every index between from (inclusive) and to (exclusive) of the source column using the operator and stores
	 * the result in target in format {@link IntegerFormats.Format#UNSIGNED_INT8}.
	 */
	private static <R, T> void mapPart(Column source, Class<R> sourceType, Function<R, T> operator,
									   UInt8CategoricalBuffer<T> target, int from, int to) {
		final ObjectReader<R> reader = Readers.objectReader(source, sourceType, to);
		reader.setPosition(from - 1);
		for (int i = from; i < to; i++) {
			R value = reader.read();
			target.set(i, operator.apply(value));
		}
	}

}