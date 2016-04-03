// Copyright (c) 2015 Amazon.com, Inc.  All rights reserved.

package com.amazon.ion.impl.bin;

import static com.amazon.ion.IonType.BLOB;
import static com.amazon.ion.IonType.BOOL;
import static com.amazon.ion.IonType.CLOB;
import static com.amazon.ion.IonType.DECIMAL;
import static com.amazon.ion.IonType.FLOAT;
import static com.amazon.ion.IonType.INT;
import static com.amazon.ion.IonType.LIST;
import static com.amazon.ion.IonType.NULL;
import static com.amazon.ion.IonType.SEXP;
import static com.amazon.ion.IonType.STRING;
import static com.amazon.ion.IonType.STRUCT;
import static com.amazon.ion.IonType.SYMBOL;
import static com.amazon.ion.IonType.TIMESTAMP;
import static com.amazon.ion.SystemSymbols.IMPORTS_SID;
import static com.amazon.ion.SystemSymbols.NAME_SID;
import static com.amazon.ion.SystemSymbols.VERSION_SID;
import static com.amazon.ion.TestUtils.hexDump;
import static com.amazon.ion.impl.bin.Symbols.systemSymbol;

import com.amazon.ion.IonException;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonType;
import com.amazon.ion.IonValue;
import com.amazon.ion.IonWriter;
import com.amazon.ion.Timestamp;
import com.amazon.ion.impl.bin.AbstractIonWriter.WriteValueOptimization;
import com.amazon.ion.impl.bin.IonRawBinaryWriter.PreallocationMode;
import com.amazon.ion.impl.bin.IonRawBinaryWriter.StreamCloseMode;
import com.amazon.ion.impl.bin.IonRawBinaryWriter.StreamFlushMode;
import com.amazon.ion.junit.Injected;
import com.amazon.ion.junit.Injected.Inject;
import com.amazon.ion.junit.IonAssert;
import com.amazon.ion.system.IonSystemBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

// TODO incorporate this into the main reader/writer tests

@RunWith(Injected.class)
public class IonRawBinaryWriterTest extends Assert
{
    private static IonSystem SYSTEM = IonSystemBuilder.standard().build();

    protected static IonSystem system()
    {
        return SYSTEM;
    }

    protected static void assertEquals(final IonValue v1, final IonValue v2)
    {
        IonAssert.assertIonEquals(v1, v2);
    }

    @Inject("preallocationMode")
    public static final PreallocationMode[] PREALLOCATION_DIMENSION = PreallocationMode.values();

    protected ByteArrayOutputStream     buffer;
    protected IonWriter                 writer;
    protected PreallocationMode         preallocationMode;

    public void setPreallocationMode(final PreallocationMode preallocationMode)
    {
        this.preallocationMode = preallocationMode;
    }

    @Before
    public final void setup() throws Exception
    {
        buffer = new ByteArrayOutputStream();
        writer = createWriter(buffer);
        writeIVMIfPossible();
    }

    @After
    public final void teardown() throws Exception
    {
        writer.close();
        buffer = null;
        writer = null;
    }

    protected IonWriter createWriter(final OutputStream out) throws IOException
    {
        return new IonRawBinaryWriter(
            BlockAllocatorProviders.basicProvider(),
            11,
            out,
            WriteValueOptimization.NONE,
            StreamCloseMode.NO_CLOSE,
            StreamFlushMode.NO_FLUSH,
            preallocationMode
        );
    }

    private void writeIVMIfPossible() throws IOException
    {
        if (writer instanceof IonRawBinaryWriter)
        {
            ((IonRawBinaryWriter) writer).writeIonVersionMarker();
        }
    }

    /** sub-classes can provide additional checks on a value as part of {@link #assertValue(String)}. */
    protected void additionalValueAssertions(final IonValue value) {}

    protected final void assertValue(final String literal) throws IOException
    {
        writer.finish();
        final byte[] data = buffer.toByteArray();
        final IonValue actual;
        try {
            actual = system().singleValue(data);
        } catch (final Exception e) {
            throw new IonException("Bad generated data:\n" + hexDump(data), e);
        }
        final IonValue expected = system().singleValue(literal);
        assertEquals(expected, actual);

        additionalValueAssertions(actual);

        // prepare for next value
        buffer.reset();
        writeIVMIfPossible();
    }

    private static class NullDesc
    {
        public final IonType type;
        public final String literal;

        public NullDesc(final IonType type, final String literal)
        {
            this.type = type;
            this.literal = literal;
        }
    }

    private static final NullDesc[] NULL_DESCS = {
        new NullDesc(NULL,      "null.null"),
        new NullDesc(BOOL,      "null.bool"),
        new NullDesc(INT,       "null.int"),
        new NullDesc(FLOAT,     "null.float"),
        new NullDesc(DECIMAL,   "null.decimal"),
        new NullDesc(TIMESTAMP, "null.timestamp"),
        new NullDesc(SYMBOL,    "null.symbol"),
        new NullDesc(STRING,    "null.string"),
        new NullDesc(CLOB,      "null.clob"),
        new NullDesc(BLOB,      "null.blob"),
        new NullDesc(LIST,      "null.list"),
        new NullDesc(SEXP,      "null.sexp"),
        new NullDesc(STRUCT,    "null.struct")
    };

    @Test
    public void testNullNull() throws Exception
    {
        writer.writeNull();
        assertValue("null.null");

        for (final NullDesc desc : NULL_DESCS)
        {
            writer.writeNull(desc.type);
            assertValue(desc.literal);
        }
    }

    @Test
    public void testBool() throws Exception
    {
        writer.writeBool(false);
        assertValue("false");

        writer.writeBool(true);
        assertValue("true");
    }

    @Test
    public void testInt() throws Exception
    {
        writer.writeInt(BigInteger.ZERO);
        assertValue("0");

        writer.writeInt(1);
        assertValue("1");

        writer.writeInt(-1);
        assertValue("-1");

        // 2 ** 64
        writer.writeInt(new BigInteger("18446744073709551616"));
        assertValue("18446744073709551616");

        writer.writeInt(Long.MIN_VALUE);
        assertValue("-0x8000000000000000");
    }

    @Test
    public void testFloat() throws Exception
    {
        writer.writeFloat(0.0);
        assertValue("0e0");

        writer.writeFloat(Double.NaN);
        assertValue("nan");

        writer.writeFloat(Double.POSITIVE_INFINITY);
        assertValue("+inf");
    }

    private static final String DECIMAL_10_DIGIT  = "1.000000001";
    private static final String DECIMAL_45_DIGIT = "1.00000000000000000000000000000000000000000001";
    @Test
    public void testDecimal() throws Exception
    {
        writer.writeDecimal(null);
        assertValue("null.decimal");

        writer.writeDecimal(BigDecimal.ZERO);
        assertValue("0d0");

        writer.writeDecimal(new BigDecimal(DECIMAL_10_DIGIT));
        assertValue(DECIMAL_10_DIGIT);

        writer.writeDecimal(new BigDecimal(DECIMAL_45_DIGIT));
        assertValue(DECIMAL_45_DIGIT);
    }

    @Test
    public void testTimestamp() throws Exception
    {
        {
            writer.writeTimestamp(null);
            assertValue("null.timestamp");
        }
        {
            final Timestamp ts = Timestamp.valueOf("2015-05-01T12:15:23Z");
            writer.writeTimestamp(ts);
            assertValue(ts.toString());
        }
        {
            final Timestamp ts = Timestamp.valueOf("2015-05-01T12:15:23.122Z");
            writer.writeTimestamp(ts);
            assertValue(ts.toString());
        }
    }

    // note that we stick to system symbols for round trip assertions
    @Test
    public void testSymbol() throws Exception
    {
        writer.writeSymbolToken(null);
        assertValue("null.symbol");

        writer.writeSymbolToken(systemSymbol(NAME_SID));
        assertValue("name");
    }

    @Test
    public void testStringInline() throws Exception
    {
        writer.writeString(null);
        assertValue("null.string");

        writer.writeString("hello");
        assertValue("'''hello'''");
    }

    private static final String STR_127 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                                          "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Test
    public void testString1ByteLength() throws Exception
    {
        assertEquals(127, STR_127.length());
        writer.writeString(STR_127);
        assertValue("'''" + STR_127 + "'''");
    }

    @Test
    public void testString2ByteLength() throws Exception
    {
        assertEquals(127, STR_127.length());
        writer.writeString(STR_127 + " ");
        assertValue("'''" + STR_127 + " '''");
    }

    // this is a length that cannot fit in up to a two byte pad length (2 ** 14)
    private static final int LONG_STRING_LENGTH = 16384;
    @Test
    public void testStringSidePatch() throws Exception
    {
        final StringBuilder buf = new StringBuilder();
        for (int i = 0; i < LONG_STRING_LENGTH; i++)
        {
            buf.append("Z");
        }
        final String bigStr = buf.toString();
        writer.writeString(bigStr);
        assertValue("'''" + bigStr + "'''");
    }

    @Test
    public void testClob() throws Exception
    {
        writer.writeClob(null);
        assertValue("null.clob");

        writer.writeClob("hello".getBytes("UTF-8"));
        assertValue("{{\"hello\"}}");
    }

    @Test
    public void testBlob() throws Exception
    {
        writer.writeBlob(null);
        assertValue("null.blob");

        writer.writeBlob("hello".getBytes("UTF-8"));
        assertValue("{{aGVsbG8=}}");
    }

    @Test
    public void testSetAnnotations() throws Exception
    {
        writer.setTypeAnnotationSymbols(systemSymbol(NAME_SID), systemSymbol(VERSION_SID));
        writer.writeString("foobar");
        assertValue("name::version::'''foobar'''");
    }

    @Test
    public void testList() throws Exception
    {
        writer.setTypeAnnotationSymbols(systemSymbol(IMPORTS_SID));
        writer.stepIn(IonType.LIST);
        {
            writer.setTypeAnnotationSymbols(systemSymbol(NAME_SID));
            writer.writeString("kumo");
        }
        writer.stepOut();
        assertValue("imports::[name::\"kumo\"]");
    }

    @Test
    public void testSexp() throws Exception
    {
        writer.stepIn(IonType.SEXP);
        {
            writer.writeString("store");
            writer.stepIn(IonType.SEXP);
            {
                writer.writeString("add");
                writer.writeInt(0);
                writer.writeInt(1);
                writer.writeInt(2);
            }
            writer.stepOut();
            writer.writeString("output");
        }
        writer.stepOut();
        assertValue("('''store''' ('''add''' 0 1 2) '''output''')");
    }

    @Test
    public void testStruct() throws Exception
    {
        writer.stepIn(IonType.STRUCT);
        {
            writer.setFieldNameSymbol(systemSymbol(NAME_SID));
            writer.writeString("kumo");

            writer.setFieldNameSymbol(systemSymbol(VERSION_SID));
            writer.writeInt(1);

            writer.setFieldNameSymbol(systemSymbol(IMPORTS_SID));
            writer.stepIn(IonType.LIST);
            {
                writer.writeInt(0);
                writer.writeInt(1);
                writer.writeInt(2);
            }
            writer.stepOut();
        }
        writer.stepOut();
        assertValue("{name:\"kumo\", version:1, imports:[0, 1, 2]}");
    }

    // TODO test large stuff...
}
