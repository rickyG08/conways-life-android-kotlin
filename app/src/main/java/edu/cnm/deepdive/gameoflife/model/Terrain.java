/*
 *  Copyright 2021 CNM Ingenuity, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package edu.cnm.deepdive.gameoflife.model;

import java.util.BitSet;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/**
 * Implements Conway's Game of Life cellular automaton (CA) on a toroidal grid of cells. The
 * standard rules for state changes are used:
 * <ul>
 *   <ol>An inactive cell with exactly 3 active neighbors will be active in the next iteration;</ol>
 *   <ol>An active cell with 2 or 3 active neighbors will be active in the next iteration;</ol>
 *   <ol>All other cells will be inactive in the next iteration.</ol>
 * </ul>
 * (These threshold values are specified as constants in this class, so they can be changed easily.)
 * <p>
 *   In one sense, this class implements a multi-state CA, in that each active cell also has an
 *   <em>age</em>, representing the number of consecutive iterations (from 1 to a clipped maximum of
 *   {@code Byte.MAX_VALUE}, or 127) that the cell has been active. The age value may be used in
 *   view shading, etc.
 * </p>
 */
public class Terrain {

  private static final int[][] neighborhood = {
      {-1, -1}, {-1, 0}, {-1, 1},
      { 0, -1},          { 0, 1},
      { 1, -1}, { 1, 0}, { 1, 1}
  };
  private static final int MIN_NEIGHBORS_BIRTH = 3;
  private static final int MAX_NEIGHBORS_BIRTH = 3;
  private static final int MIN_NEIGHBORS_SURVIVAL = 2;
  private static final int MAX_NEIGHBORS_SURVIVAL = 3;

  private final Object lock = new Object();
  private final int size;
  private final Checksum crc;
  private final BitSet bitSet;

  private byte[][] cells;
  private byte[][] next;
  private long iterationCount;
  private int population;
  private long checksum;

  /**
   * Initializes an empty 2-dimensional toroidal cell grid of the specified {@code size} (height and
   * width, both periodic).
   *
   * @param size Height and width (both periodic) of the new toroidal cell grid.
   */
  public Terrain(int size) {
    this.size = size;
    crc = new CRC32();
    bitSet = new BitSet(size);
    cells = new byte[size][size];
    next = new byte[size][size];
    population = 0;
    iterationCount = 0;
  }

  /**
   * Initializes a 2-dimensional toroidal cell grid of the specified {@code size} (height and width,
   * both periodic), with cells activated randomly according to the specified {@code density}, using
   * {@code rng} as the source of randomness.
   *
   * @param size Height and width (both periodic) of the new toroidal cell grid.
   * @param density Approximate density of active cells.
   * @param rng Source of randomness.
   */
  public Terrain(int size, double density, Random rng) {
    this(size);
    for (byte[] row : cells) {
      for (int colIndex = 0; colIndex < row.length; colIndex++) {
        if (rng.nextDouble() < density) {
          row[colIndex] = 1;
          population++;
          bitSet.set(colIndex);
        } else {
          bitSet.clear(colIndex);
        }
      }
      byte[] rowBytes = bitSet.toByteArray();
      crc.update(rowBytes, 0, rowBytes.length);
    }
    checksum = crc.getValue();
  }

  /**
   * Returns the size (periodic height and width) of the cell grid.
   */
  public int getSize() {
    return size;
  }

  /**
   * Returns the number of iterations that have been performed on this instance.
   */
  public long getIterationCount() {
    return iterationCount;
  }

  /**
   * Returns the number of currently active cells.
   */
  public int getPopulation() {
    return population;
  }

  /**
   * Returns the most recently computed checksum for this instance. This may be used to detect an
   * arrangement consisting entirely of oscillators and still lifes.
   */
  public long getChecksum() {
    return checksum;
  }

  /**
   * Returns the age of the cell in the location specified by {@code rowIndex} and {@code colIndex}.
   * A positive age indicates an active cell, while a zero (or negative) value indicates an inactive
   * cell.
   *
   * @param rowIndex Vertical position of the specified cell in the grid.
   * @param colIndex Horizontal position of the specified cell in the grid.
   * @return Age of the specified cell.
   */
  public byte get(int rowIndex, int colIndex) {
    return cells[rowIndex][colIndex];
  }

  /**
   * Modifies the age/state of the cell in the location specified by {@code rowIndex} and {@code
   * colIndex}. A positive age indicates an active cell, while a zero (or negative) value indicates
   * an inactive cell. <strong>Note:</strong> the value returned by {@link #getChecksum()} is not
   * automatically updated when cell age/state is set in this fashion.
   *
   * @param rowIndex Vertical position of the specified cell in the grid.
   * @param colIndex Horizontal position of the specified cell in the grid.
   * @param age Specified age/state of the cell.
   */
  public void set(int rowIndex, int colIndex, byte age) {
    byte previous = cells[rowIndex][colIndex];
    cells[rowIndex][colIndex] = age;
    if (previous <= 0 && age > 0) {
      population++;
    } else if (previous > 0 && age <= 0) {
      population--;
    }
  }

  /**
   * Performs one iteration of the CA, updating cell states over the entire grid, tallying the
   * population (as returned by {@link #getPopulation()}), and recomputing the value returned by
   * {@link #getChecksum()}.
   */
  public void iterate() {
    crc.reset();
    int nextPopulation = 0;
    for (int rowIndex = 0; rowIndex < size; rowIndex++) {
      for (int colIndex = 0; colIndex < size; colIndex++) {
        int count = countNeighbors(rowIndex, colIndex);
        byte age = nextGenerationAge(cells[rowIndex][colIndex], count);
        if (age > 0) {
          nextPopulation++;
          bitSet.set(colIndex);
        } else {
          bitSet.clear(colIndex);
        }
        next[rowIndex][colIndex] = age;
      }
      byte[] rowBytes = bitSet.toByteArray();
      crc.update(rowBytes, 0, rowBytes.length);
    }
    byte[][] temp = cells;
    synchronized (lock) {
      cells = next;
      next = temp;
      population = nextPopulation;
      checksum = crc.getValue();
    }
    iterationCount++;
  }

  /**
   * Copies the cell grid (in its current state) to the specified {@code byte[][]} destination
   * array. This destination must already exist, and must be of the same as the cell grid.
   *
   * @param cells Destination {@code byte[][]} array.
   */
  public void copyCells(byte[][] cells) {
    synchronized (lock) {
      for (int rowIndex = 0; rowIndex < size; rowIndex++) {
        System.arraycopy(this.cells[rowIndex], 0, cells[rowIndex], 0, size);
      }
    }
  }

  private int countNeighbors(int rowIndex, int colIndex) {
    int count = 0;
    for (int[] offsets : neighborhood) {
      if (cells[Math.floorMod(rowIndex + offsets[0], size)]
          [Math.floorMod(colIndex + offsets[1], size)] > 0) {
        count++;
      }
    }
    return count;
  }

  private byte nextGenerationAge(byte age, int numNeighbors) {
    byte next;
    if (age == 0) {
      next = (byte) ((numNeighbors >= MIN_NEIGHBORS_BIRTH && numNeighbors <= MAX_NEIGHBORS_BIRTH)
          ? 1
          : 0);
    } else if (numNeighbors >= MIN_NEIGHBORS_SURVIVAL && numNeighbors <= MAX_NEIGHBORS_SURVIVAL) {
      next = (byte) Math.min(age + 1, Byte.MAX_VALUE);
    } else {
      next = 0;
    }
    return next;
  }

}