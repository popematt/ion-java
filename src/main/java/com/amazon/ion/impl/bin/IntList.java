package com.amazon.ion.impl.bin;

/**
 * A list of integer values that grows as necessary.
 *
 * Unlike {@link java.util.List}, IntList does not require each int to be boxed. This makes it helpful in use cases
 * where storing {@link Integer} leads to excessive time spent in garbage collection.
 */
public class IntList {
    public static final int DEFAULT_INITIAL_CAPACITY = 8;
    private static final int GROWTH_MULTIPLIER = 2;
    private int[] data;
    private int numberOfValues;
//    private int capacity;

    /**
     * Constructs a new IntList with a capacity of {@link IntList#DEFAULT_INITIAL_CAPACITY}.
     */
    public IntList() {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    /**
     * Constructs a new IntList with the specified capacity.
     * @param initialCapacity   The number of ints that can be stored in this IntList before it will need to be
     *                          reallocated.
     */
    public IntList(final int initialCapacity) {
        data = new int[initialCapacity];
//        capacity = initialCapacity;
        numberOfValues = 0;
    }

    /**
     * Constructs a new IntList that contains all the elements of the given IntList.
     * @param other the IntList to copy.
     */
    public IntList(final IntList other) {
        this.numberOfValues = other.numberOfValues;
        this.data = new int[other.data.length];
//        capacity = data.length;
        System.arraycopy(other.data, 0, this.data, 0, numberOfValues);
    }

    /**
     * Accessor.
     * @return  The number of ints currently stored in the list.
     */
    public int size() {
        return numberOfValues;
    }

    /**
     * @return {@code true} if there are ints stored in the list.
     */
    public boolean isEmpty() {
        return numberOfValues == 0;
    }

    /**
     * Empties the list.
     *
     * Note that this method does not shrink the size of the backing data store.
     */
    public void clear() {
        numberOfValues = 0;
    }

    /**
     * Returns the {@code index}th int in the list.
     * @param index     The list index of the desired int.
     * @return          The int at index {@code index} in the list.
     * @throws IndexOutOfBoundsException    if the index is negative or greater than the number of ints stored in the
     *                                      list.
     */
    public int get(int index) {
        if (index < 0 || index >= numberOfValues) {
            throw new IndexOutOfBoundsException(
                    "Invalid index " + index + " requested from IntList with " + numberOfValues + " values."
            );
        }
        return data[index];
    }

    /**
     * Increases the size of the list without modifying the backing data.
     * @return the position that was reserved.
     */
    public int reserve() {
        return numberOfValues++;
    }

    /**
     * Appends an int to the end of the list, growing the list if necessary.
     * @param value     The int to add to the end of the list.
     */
    public void add(int value) {
        int n = numberOfValues;
        int newNumberOfValues = n + 1;
        int[] data = ensureCapacity(newNumberOfValues);
        data[n] = value;
        numberOfValues = newNumberOfValues;
    }

    public void add2(int value0, int value1) {
        int n = numberOfValues;
        int newNumberOfValues = n + 2;
        int[] data = ensureCapacity(newNumberOfValues);
        data[n] = value0;
        data[n + 1] = value1;
        numberOfValues = newNumberOfValues;
    }

    public void add3(int value0, int value1, int value2) {
        int n = numberOfValues;
        int newNumberOfValues = n + 3;
        int[] data = ensureCapacity(newNumberOfValues);
        data[n] = value0;
        data[n + 1] = value1;
        data[n + 2] = value2;
        numberOfValues = newNumberOfValues;
    }

    public void addAll(int[] values) {
        int valuesLength = values.length;
        int thisNumberOfValues = numberOfValues;
        int newNumberOfValues = valuesLength + thisNumberOfValues;
        int[] data = ensureCapacity(newNumberOfValues);
        System.arraycopy(values, 0, data, thisNumberOfValues, valuesLength);
        this.numberOfValues = newNumberOfValues;
    }

    public void addAll(IntList values) {
        int thisNumberOfValues = this.numberOfValues;
        int otherNumberOfValues = values.numberOfValues;
        int newNumberOfValues =  thisNumberOfValues + otherNumberOfValues;
        int[] data = ensureCapacity(newNumberOfValues);
        System.arraycopy(values.data, 0, data, thisNumberOfValues, otherNumberOfValues);
        this.numberOfValues = newNumberOfValues;
    }

    public void addSlice(IntList values, int startInclusive, int length) {
        int thisNumberOfValues = this.numberOfValues;
        int newNumberOfValues = thisNumberOfValues + length;
        int[] data = ensureCapacity(newNumberOfValues);
        System.arraycopy(values.data, startInclusive, data, thisNumberOfValues, length);
        this.numberOfValues = newNumberOfValues;
    }

    public void set(int index, int value) {
        if (index < 0 || index >= numberOfValues) {
            throw new IndexOutOfBoundsException();
        }
        data[index] = value;
    }

    public int[] toArray() {
        int thisNumberOfValues = this.numberOfValues;
        int[] copy = new int[thisNumberOfValues];
        System.arraycopy(data, 0, copy, 0, thisNumberOfValues);
        return copy;
    }

    private int[] ensureCapacity(int minCapacity) {
        int[] data = this.data;
        int capacity = data.length;
        if (minCapacity > capacity) {
            int newCapacity = minCapacity * GROWTH_MULTIPLIER;
            int[] newData = new int[newCapacity];
            System.arraycopy(data, 0, newData, 0, capacity);
            this.data = newData;
            return newData;
        }
        return data;
    }

//    /**
//     * Reallocates the backing array to accommodate storing more ints.
//     */
//    private void grow() {
//        // grow(0);
//    }
//
//    /**
//     * Reallocates the backing array to accommodate storing more ints.
//     */
//    private void grow(int minCapacity) {
//        int newCapacity = Math.max(capacity, minCapacity) * GROWTH_MULTIPLIER;
//        int[] newData = new int[newCapacity];
//        capacity = newCapacity;
//        System.arraycopy(data, 0, newData, 0, data.length);
//        data = newData;
//    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("IntList{data=[");
        if (numberOfValues > 0) {
            for (int m = 0; m < numberOfValues; m++) {
                builder.append(data[m]).append(",");
            }
        }
        builder.append("]}");
        return builder.toString();
    }
}
