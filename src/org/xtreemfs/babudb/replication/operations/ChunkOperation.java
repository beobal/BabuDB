/*
 * Copyright (c) 2009, Jan Stender, Bjoern Kolbeck, Mikael Hoegqvist,
 *                     Felix Hupfeld, Felix Langner, Zuse Institute Berlin
 * 
 * Licensed under the BSD License, see LICENSE file for details.
 * 
 */
package org.xtreemfs.babudb.replication.operations;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.xtreemfs.babudb.interfaces.Chunk;
import org.xtreemfs.babudb.interfaces.ReplicationInterface.chunkRequest;
import org.xtreemfs.babudb.interfaces.ReplicationInterface.chunkResponse;
import org.xtreemfs.babudb.interfaces.utils.Serializable;
import org.xtreemfs.babudb.replication.Request;
import org.xtreemfs.include.common.buffer.BufferPool;
import org.xtreemfs.include.common.buffer.ReusableBuffer;
import org.xtreemfs.include.common.logging.Logging;

/**
 * {@link Operation} to request a {@link Chunk} from the master.
 * 
 * @since 05/03/2009
 * @author flangner
 */

public class ChunkOperation extends Operation {

    private final int procId;
    
    public ChunkOperation() {
        this.procId = new chunkRequest().getTag();
    }

    /*
     * (non-Javadoc)
     * @see org.xtreemfs.babudb.replication.operations.Operation#getProcedureId()
     */
    @Override
    public int getProcedureId() {
        return procId;
    }

    /*
     * (non-Javadoc)
     * @see org.xtreemfs.babudb.replication.operations.Operation#parseRPCMessage(org.xtreemfs.babudb.replication.Request)
     */
    @Override
    public Serializable parseRPCMessage(Request rq) {
        chunkRequest amr = new chunkRequest();
        rq.deserializeMessage(amr);
        
        return null;
    }

    /*
     * (non-Javadoc)
     * @see org.xtreemfs.babudb.replication.operations.Operation#startInternalEvent(java.lang.Object[])
     */
    @Override
    public void startInternalEvent(Object[] args) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /*
     * (non-Javadoc)
     * @see org.xtreemfs.babudb.replication.operations.Operation#startRequest(org.xtreemfs.babudb.replication.Request)
     */
    @Override
    public void startRequest(Request rq) {
        chunkRequest request = (chunkRequest) rq.getRequestMessage();
        Chunk chunk = request.getChunk();
        int length = (int) (chunk.getEnd() - chunk.getBegin());
      
        Logging.logMessage(Logging.LEVEL_INFO, this, "CHUNK request received: %s", chunk.toString());
        
        FileChannel channel = null;
        ReusableBuffer payload = null;
        try {
            // get the requested chunk
            channel = new FileInputStream(chunk.getFileName()).getChannel();
            ByteBuffer buffer = ByteBuffer.allocate(length);
            if (channel.read(buffer, chunk.getBegin()) != length) throw new Exception();          
            buffer.flip();
            payload = new ReusableBuffer(buffer);
            rq.sendSuccess(new chunkResponse(payload));
            
        } catch (Exception e) {
            rq.sendReplicationException(ErrNo.FILE_UNAVAILABLE);
        } finally {
            try {
                if (channel != null) channel.close();
            } catch (IOException e) { /* ignored */ }
            
            if (payload!=null) BufferPool.free(payload);
        }
    }

    /*
     * (non-Javadoc)
     * @see org.xtreemfs.babudb.replication.operations.Operation#canBeDisabled()
     */
    @Override
    public boolean canBeDisabled() {
        return true;
    }
}