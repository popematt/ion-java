package com.amazon.ion.v3

import com.amazon.ion.*
import com.amazon.ion.v3.ion_reader.ReaderManager
import java.nio.ByteBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ReaderManagerTest {

    @Test
    fun test() {
        val manager = ReaderManager()

        manager.pushReader(DummyReader("A"))
        assertEquals(1, manager.readerDepth)
        assertEquals(0, manager.containerDepth)
        assertFalse(manager.isTopAContainer())

        manager.pushReader(DummyReader("B"))
        assertEquals(2, manager.readerDepth)
        assertEquals(0, manager.containerDepth)
        assertFalse(manager.isTopAContainer())

        manager.pushContainer(DummyReader("C"))
        assertEquals(3, manager.readerDepth)
        assertEquals(1, manager.containerDepth)
        assertTrue(manager.isTopAContainer())

        manager.pushReader(DummyReader("D"))
        assertEquals(4, manager.readerDepth)
        assertEquals(1, manager.containerDepth)
        assertFalse(manager.isTopAContainer())

        manager.pushContainer(DummyReader("E"))
        assertEquals(5, manager.readerDepth)
        assertEquals(2, manager.containerDepth)
        assertTrue(manager.isTopAContainer())


        assertEquals(DummyReader("D"), manager.popContainer())
        assertEquals(DummyReader("B"), manager.popContainer())

        assertThrows<IonException> { manager.popContainer() }
    }

    @Test
    fun test2() {
        val manager = ReaderManager()

        manager.pushReader(DummyReader("A"))
        assertEquals(1, manager.readerDepth)
        assertEquals(0, manager.containerDepth)

        manager.pushReader(DummyReader("B"))
        assertEquals(2, manager.readerDepth)
        assertEquals(0, manager.containerDepth)

        manager.pushContainer(DummyReader("C"))
        assertEquals(3, manager.readerDepth)
        assertEquals(1, manager.containerDepth)

        manager.pushReader(DummyReader("D"))
        assertEquals(4, manager.readerDepth)
        assertEquals(1, manager.containerDepth)

        manager.pushContainer(DummyReader("E"))
        assertEquals(5, manager.readerDepth)
        assertEquals(2, manager.containerDepth)

        manager.popReader()
        assertEquals(4, manager.readerDepth)
        assertEquals(1, manager.containerDepth)

        manager.popReader()
        assertEquals(3, manager.readerDepth)
        assertEquals(1, manager.containerDepth)

        manager.popReader()
        assertEquals(2, manager.readerDepth)
        assertEquals(0, manager.containerDepth)

        manager.popReader()
        assertEquals(1, manager.readerDepth)
        assertEquals(0, manager.containerDepth)

        manager.popReader()
        assertEquals(0, manager.readerDepth)
        assertEquals(0, manager.containerDepth)
    }


    private data class DummyReader(val id: String, var isClosed: Boolean = false): ValueReader {
        override fun nextToken(): Int {
            TODO("Not yet implemented")
        }

        override fun currentToken(): Int {
            TODO("Not yet implemented")
        }

        override fun isTokenSet(): Boolean {
            TODO("Not yet implemented")
        }

        override fun ionType(): IonType? {
            TODO("Not yet implemented")
        }

        override fun valueSize(): Int {
            TODO("Not yet implemented")
        }

        override fun skip() {
            TODO("Not yet implemented")
        }

        override fun nullValue(): IonType {
            TODO("Not yet implemented")
        }

        override fun booleanValue(): Boolean {
            TODO("Not yet implemented")
        }

        override fun longValue(): Long {
            TODO("Not yet implemented")
        }

        override fun stringValue(): String {
            TODO("Not yet implemented")
        }

        override fun symbolValue(): String? {
            TODO("Not yet implemented")
        }

        override fun symbolValueSid(): Int {
            TODO("Not yet implemented")
        }

        override fun lookupSid(sid: Int): String? {
            TODO("Not yet implemented")
        }

        override fun clobValue(): ByteBuffer {
            TODO("Not yet implemented")
        }

        override fun blobValue(): ByteBuffer {
            TODO("Not yet implemented")
        }

        override fun listValue(): ListReader {
            TODO("Not yet implemented")
        }

        override fun sexpValue(): SexpReader {
            TODO("Not yet implemented")
        }

        override fun structValue(): StructReader {
            TODO("Not yet implemented")
        }

        override fun annotations(): AnnotationIterator {
            TODO("Not yet implemented")
        }

        override fun timestampValue(): Timestamp {
            TODO("Not yet implemented")
        }

        override fun doubleValue(): Double {
            TODO("Not yet implemented")
        }

        override fun decimalValue(): Decimal {
            TODO("Not yet implemented")
        }

        override fun ivm(): Short {
            TODO("Not yet implemented")
        }

        override fun getIonVersion(): Short {
            TODO("Not yet implemented")
        }

        override fun seekTo(position: Int) {
            TODO("Not yet implemented")
        }

        override fun position(): Int {
            TODO("Not yet implemented")
        }

        override fun close() {
            println("Closing: $id")
            isClosed = true
        }

    }
}
