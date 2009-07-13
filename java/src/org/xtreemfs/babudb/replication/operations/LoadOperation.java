/*
 * Copyright (c) 2009, Jan Stender, Bjoern Kolbeck, Mikael Hoegqvist,
 *                     Felix Hupfeld, Felix Langner, Zuse Institute Berlin
 * 
 * Licensed under the BSD License, see LICENSE file for details.
 * 
 */
package org.xtreemfs.babudb.replication.operations;

import java.io.File;

import org.xtreemfs.babudb.interfaces.DBFileMetaData;
import org.xtreemfs.babudb.interfaces.DBFileMetaDataSet;
import org.xtreemfs.babudb.interfaces.ReplicationInterface.loadRequest;
import org.xtreemfs.babudb.interfaces.ReplicationInterface.loadResponse;
import org.xtreemfs.babudb.interfaces.utils.Serializable;
import org.xtreemfs.babudb.lsmdb.LSMDatabase;
import org.xtreemfs.babudb.replication.MasterRequestDispatcher;
import org.xtreemfs.babudb.replication.Request;

/**
 * {@link Operation} to request a {@link DBFileMetaDataSet} from the master.
 * 
 * @since 05/03/2009
 * @author flangner
 */

public class LoadOperation extends Operation {

    private final int procId;
    private final MasterRequestDispatcher dispatcher;
    
    public LoadOperation(MasterRequestDispatcher dispatcher) {
        this.procId = new loadRequest().getOperationNumber();
        this.dispatcher = dispatcher;
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
        loadRequest rpcrq = new loadRequest();
        rq.deserializeMessage(rpcrq);
        
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
        DBFileMetaDataSet result = new DBFileMetaDataSet();
        loadRequest request = (loadRequest) rq.getRequestMessage();
        
        if (dispatcher.lastOnView != null && request.getLsn().equals(dispatcher.lastOnView))
            rq.sendSuccess((Serializable) new DBFileMetaDataSet());
        else {    
            // add the DB-structure-file metadata
            int chunkSize = dispatcher.chunkSize;
            String path = dispatcher.db.getDBConfigPath();
            long length = new File(path).length();
            result.add(new DBFileMetaData(path,length,chunkSize));

            // add the latest snapshot files for every DB,
            // if available
            for (LSMDatabase db : dispatcher.db.databases.values())
                result.addAll(db.getLastestSnapshotFiles(chunkSize));
            
            rq.sendSuccess(new loadResponse(result));
        }
    }
}