package com.amazon.ion.impl.macro;

import com.amazon.ion.IonBufferConfiguration;
import com.amazon.ion.Timestamp;
import com.amazon.ion.impl.IonReaderContinuableCoreBinary;

import java.io.InputStream;

public class IonReaderContinuableCoreMacroAwareBinary extends IonReaderContinuableCoreBinary {

    protected IonReaderContinuableCoreMacroAwareBinary(IonBufferConfiguration configuration, byte[] bytes, int offset, int length) {
        super(configuration, bytes, offset, length);
    }

    protected IonReaderContinuableCoreMacroAwareBinary(IonBufferConfiguration configuration, InputStream inputStream, byte[] alreadyRead, int alreadyReadOff, int alreadyReadLen) {
        super(configuration, inputStream, alreadyRead, alreadyReadOff, alreadyReadLen);
    }

    private boolean isInMacroExpansion = false;
    private IonReaderShim macroExpander = null;


    @Override
    public Timestamp timestampValue() {
        if (isInMacroExpansion) {
            return macroExpander.timestampValue();
        } else {
            return super.timestampValue();
        }
    }


}
