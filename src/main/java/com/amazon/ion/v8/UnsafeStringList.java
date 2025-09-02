package com.amazon.ion.v8;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * Based off of IntList, this is actually just an ArrayList that allows unsafe access to the underlying Array.
 *
 *
 *
 * A list of string values that grows as necessary.
 *
 * Unlike {@link List}, IntList does not require each int to be boxed. This makes it helpful in use cases
 * where storing {@link Integer} leads to excessive time spent in garbage collection.
 */
public class UnsafeStringList implements List<String> {
    public static final int DEFAULT_INITIAL_CAPACITY = 8;
    private static final int GROWTH_MULTIPLIER = 2;
    private String[] data;
    private int numberOfValues;
//    private int capacity;

    /**
     * Constructs a new IntList with a capacity of {@link UnsafeStringList#DEFAULT_INITIAL_CAPACITY}.
     */
    public UnsafeStringList() {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    /**
     * Constructs a new IntList with the specified capacity.
     * @param initialCapacity   The number of ints that can be stored in this IntList before it will need to be
     *                          reallocated.
     */
    public UnsafeStringList(final int initialCapacity) {
        data = newT(initialCapacity);
//        capacity = initialCapacity;
        numberOfValues = 0;
    }

    private static String[] newT(final int capacity) {
        return new String[capacity];
    }

    /**
     * Constructs a new IntList that contains all the elements of the given IntList.
     * @param other the IntList to copy.
     */
    public UnsafeStringList(final UnsafeStringList other) {
        this.numberOfValues = other.numberOfValues;
        this.data = newT(other.data.length);
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
    public String get(int index) {
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
    public boolean add(String value) {
        int n = numberOfValues;
        int newNumberOfValues = n + 1;
        String[] data = ensureCapacity(newNumberOfValues);
        data[n] = value;
        numberOfValues = newNumberOfValues;
        return true;
    }

    public void add2(String value0, String value1) {
        int n = numberOfValues;
        int newNumberOfValues = n + 2;
        String[] data = ensureCapacity(newNumberOfValues);
        data[n] = value0;
        data[n + 1] = value1;
        numberOfValues = newNumberOfValues;
    }

    public void add3(String value0, String value1, String value2) {
        int n = numberOfValues;
        int newNumberOfValues = n + 3;
        String[] data = ensureCapacity(newNumberOfValues);
        data[n] = value0;
        data[n + 1] = value1;
        data[n + 2] = value2;
        numberOfValues = newNumberOfValues;
    }

    public void addAll(int[] values) {
        int valuesLength = values.length;
        int thisNumberOfValues = numberOfValues;
        int newNumberOfValues = valuesLength + thisNumberOfValues;
        String[] data = ensureCapacity(newNumberOfValues);
        System.arraycopy(values, 0, data, thisNumberOfValues, valuesLength);
        this.numberOfValues = newNumberOfValues;
    }

    public void addAll(UnsafeStringList values) {
        int thisNumberOfValues = this.numberOfValues;
        int otherNumberOfValues = values.numberOfValues;
        int newNumberOfValues =  thisNumberOfValues + otherNumberOfValues;
        String[] data = ensureCapacity(newNumberOfValues);
        System.arraycopy(values.data, 0, data, thisNumberOfValues, otherNumberOfValues);
        this.numberOfValues = newNumberOfValues;
    }

    public void addSlice(UnsafeStringList values, int startInclusive, int length) {
        int thisNumberOfValues = this.numberOfValues;
        int newNumberOfValues = thisNumberOfValues + length;
        String[] data = ensureCapacity(newNumberOfValues);
        System.arraycopy(values.data, startInclusive, data, thisNumberOfValues, length);
        this.numberOfValues = newNumberOfValues;
    }

    public void truncate(int length) {
        numberOfValues = length;
    }

    public String set(int index, String value) {
        if (index < 0 || index >= numberOfValues) {
            throw new IndexOutOfBoundsException();
        }
        data[index] = value;
        return null;
    }

    public String[] toArray() {
        int thisNumberOfValues = this.numberOfValues;
        Object[] copy = new Object[thisNumberOfValues];
        System.arraycopy(data, 0, copy, 0, thisNumberOfValues);
        return (String[]) copy;
    }

    private String[] ensureCapacity(int minCapacity) {
        String[] data = this.data;
        int capacity = data.length;
        if (minCapacity > capacity) {
            int newCapacity = minCapacity * GROWTH_MULTIPLIER;
            String[] newData = newT(newCapacity);
            System.arraycopy(data, 0, newData, 0, capacity);
            this.data = newData;
            return newData;
        }
        return data;
    }

    public String[] unsafeGetArray() {
        return data;
    }

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


    @Override
    public boolean contains(Object o) {
        return false;
    }

    @NotNull
    @Override
    public Iterator<String> iterator() {
        return null;
    }

    @NotNull
    @Override
    public <T1> T1[] toArray(@NotNull T1[] a) {
        return null;
    }

    @Override
    public boolean remove(Object o) {
        return false;
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        return false;
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends String> c) {
        return false;
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        return false;
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        return false;
    }

    @Override
    public boolean addAll(int index, @NotNull Collection<? extends String> c) {
        return false;
    }

    @Override
    public void add(int index, String element) {

    }

    @Override
    public String remove(int index) {
        return null;
    }

    @Override
    public int indexOf(Object o) {
        return 0;
    }

    @Override
    public int lastIndexOf(Object o) {
        return 0;
    }

    @NotNull
    @Override
    public ListIterator<String> listIterator() {
        return null;
    }

    @NotNull
    @Override
    public ListIterator<String> listIterator(int index) {
        return null;
    }

    @NotNull
    @Override
    public List<String> subList(int fromIndex, int toIndex) {
        return null;
    }
}
