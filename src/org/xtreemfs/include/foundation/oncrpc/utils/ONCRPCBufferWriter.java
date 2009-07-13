/*  Copyright (c) 2009 Konrad-Zuse-Zentrum fuer Informationstechnik Berlin.

    This file is part of XtreemFS. XtreemFS is part of XtreemOS, a Linux-based
    Grid Operating System, see <http://www.xtreemos.eu> for more details.
    The XtreemOS project has been developed with the financial support of the
    European Commission's IST program under contract #FP6-033576.

    XtreemFS is free software: you can redistribute it and/or modify it under
    the terms of the GNU General Public License as published by the Free
    Software Foundation, either version 2 of the License, or (at your option)
    any later version.

    XtreemFS is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with XtreemFS. If not, see <http://www.gnu.org/licenses/>.
*/
/*
 * AUTHORS: Björn Kolbeck (ZIB)
 */

package org.xtreemfs.include.foundation.oncrpc.utils;

import java.util.ArrayList;
import java.util.List;
import org.xtreemfs.include.common.buffer.BufferPool;
import org.xtreemfs.include.common.buffer.ReusableBuffer;

/**
 *
 * @author bjko
 */
public class ONCRPCBufferWriter {

    public static final int BUFF_SIZE = 1024*8;

    private final int bufSize;

    private final List<ReusableBuffer> buffers;

    private int   currentBuffer;

    public ONCRPCBufferWriter(int bufSize) {
        this.bufSize = bufSize;
        buffers = new ArrayList<ReusableBuffer>(15);
        buffers.add(BufferPool.allocate(bufSize));
        currentBuffer = 0;
    }

    private ReusableBuffer checkAndGetBuffer(int requiredSpace) {
        final ReusableBuffer currentBuf = buffers.get(currentBuffer);
        if (currentBuf.remaining() >= requiredSpace) {
            return currentBuf;
        } else {
            currentBuffer++;
            final ReusableBuffer buf = BufferPool.allocate(bufSize);
            buffers.add(buf);
            return buf;
        }
    }
    
    public void flip() {
        for (ReusableBuffer buffer : buffers) {
            buffer.flip();
        }
        currentBuffer = 0;
    }
    
    public void freeBuffers() {
        for (ReusableBuffer buffer : buffers) {
            BufferPool.free(buffer);
        }
    }
    
    public void put(byte data) {
        checkAndGetBuffer(Byte.SIZE/8).put(data);
    }

    public void put(byte[] data) {
        checkAndGetBuffer(Byte.SIZE/8*data.length).put(data);
    }

    public void putShort(short data) {
        checkAndGetBuffer(Short.SIZE/8).putShort(data);
    }

    public void putInt(int data) {
        checkAndGetBuffer(Integer.SIZE/8).putInt(data);
    }

    public void putLong(long data) {
        checkAndGetBuffer(Long.SIZE/8).putLong(data);
    }

    public void putDouble(double d) {
        checkAndGetBuffer(Double.SIZE/8).putDouble(d);
    }

    public void put(ReusableBuffer otherBuffer) {
        currentBuffer++;
        /*final ReusableBuffer vb = otherBuffer.createViewBuffer();
        vb.position(otherBuffer.limit());
        buffers.add(vb);*/
        otherBuffer.position(otherBuffer.limit());
        buffers.add(otherBuffer);
    }

    public List<ReusableBuffer> getBuffers() {
        return this.buffers;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ONCRPCBufferWriter with "+this.buffers.size()+" buffers\n");
        for (ReusableBuffer buffer : buffers) {
            sb.append("buffer position="+buffer.position()+" limit="+buffer.limit()+"\n");
        }
        return sb.toString();
    }







}
