/*
 * Copyright (c) 2009 - 2011, Jan Stender, Bjoern Kolbeck, Mikael Hoegqvist,
 *                     Felix Hupfeld, Felix Langner, Zuse Institute Berlin
 * 
 * Licensed under the BSD License, see LICENSE file for details.
 * 
 */
package org.xtreemfs.babudb.replication.service.logic;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import org.xtreemfs.babudb.api.database.DatabaseRequestListener;
import org.xtreemfs.babudb.api.exception.BabuDBException;
import org.xtreemfs.babudb.log.LogEntry;
import org.xtreemfs.babudb.log.LogEntryException;
import org.xtreemfs.babudb.lsmdb.LSN;
import org.xtreemfs.babudb.replication.BabuDBInterface;
import org.xtreemfs.babudb.replication.service.Pacemaker;
import org.xtreemfs.babudb.replication.service.ReplicationStage;
import org.xtreemfs.babudb.replication.service.ReplicationStage.Range;
import org.xtreemfs.babudb.replication.service.SlaveView;
import org.xtreemfs.babudb.replication.service.ReplicationStage.ConnectionLostException;
import org.xtreemfs.babudb.replication.service.clients.ClientResponseFuture;
import org.xtreemfs.babudb.replication.service.clients.MasterClient;
import org.xtreemfs.babudb.replication.transmission.ErrorCode;
import org.xtreemfs.babudb.replication.transmission.FileIOInterface;
import org.xtreemfs.babudb.replication.transmission.PBRPCClientAdapter.ErrorCodeException;
import org.xtreemfs.foundation.buffer.BufferPool;
import org.xtreemfs.foundation.buffer.ReusableBuffer;
import org.xtreemfs.foundation.logging.Logging;

import static org.xtreemfs.babudb.replication.service.logic.LogicID.*;
/**
 * <p>Requests missing {@link LogEntry}s at the 
 * master and inserts them into the DBS.</p>
 * 
 * @author flangner
 * @since 06/08/2009
 */

public class RequestLogic extends Logic {
    
    /** checksum algorithm used to deserialize logEntries */
    private final Checksum              checksum = new CRC32();

    /**
     * @param stage
     * @param pacemaker
     * @param slaveView
     * @param fileIO
     * @param babuInterface
     */
    public RequestLogic(ReplicationStage stage, Pacemaker pacemaker, 
            SlaveView slaveView, FileIOInterface fileIO, 
            BabuDBInterface babuInterface) {
        super(stage, pacemaker, slaveView, fileIO, babuInterface);
    }
    
    /*
     * (non-Javadoc)
     * @see org.xtreemfs.babudb.replication.stages.logic.Logic#getId()
     */
    @Override
    public LogicID getId() {
        return REQUEST;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() throws InterruptedException, ConnectionLostException{
        LSN lsnAtLeast = stage.missing.end;
        
        Logging.logMessage(Logging.LEVEL_INFO, this, 
                "Replica-range is missing: from %s to %s", 
                stage.missing.start.toString(),
                lsnAtLeast.toString());
        
        // get the missing logEntries
        ClientResponseFuture<ReusableBuffer[]> rp = null;    
        ReusableBuffer[] logEntries = null;
        
        
        MasterClient master = this.slaveView.getSynchronizationPartner(
                lsnAtLeast);
        
        Logging.logMessage(Logging.LEVEL_INFO, this, "Replica-Range will be" +
        		" retrieved from %s.", master.getDefaultServerAddress());
        
        try {
            rp = master.replica(stage.missing.start, stage.missing.end);
            logEntries = rp.get();
            
            // enhancement if the request had detected a master-failover
            if (logEntries.length == 0) {
                stage.lastOnView.set(babuInterface.getState());
                babuInterface.checkpoint();
                stage.lastInserted = babuInterface.getState();
                finish();
                return;
            }
            
            final AtomicInteger count = new AtomicInteger(logEntries.length);
            // insert all logEntries
            LSN check = null;
            for (ReusableBuffer le : logEntries) {
                try {
                    final LogEntry logentry = 
                        LogEntry.deserialize(le, this.checksum);
                    final LSN lsn = logentry.getLSN();
                    
                    // assertion whether the received entry does match the order 
                    // or not
                    assert (check == null || 
                           (check.getViewId() == lsn.getViewId() && 
                            check.getSequenceNo()+1L == lsn.getSequenceNo()) ||
                            check.getViewId()+1 == lsn.getViewId() &&
                            lsn.getSequenceNo() == 1L) : "ERROR: last LSN (" +
                            check.toString() + ") received LSN (" + 
                            lsn.toString() + ")!";
                    check = lsn;
                    
                    // we have to switch the log-file
                    if (lsn.getSequenceNo() == 1L && 
                        stage.lastInserted.getViewId() < lsn.getViewId()) {
                        
                        this.stage.lastOnView.set(this.babuInterface.getState());
                        this.babuInterface.checkpoint();
                        stage.lastInserted = this.babuInterface.getState();
                    }
                    
                    this.babuInterface.appendToLocalPersistenceManager(logentry, 
                            new DatabaseRequestListener<Object>() {
                        
                        @Override
                        public void finished(Object result, Object context) {
                            synchronized (count) {
                                stage.lastInserted = lsn;
                                if (count.decrementAndGet() == 0)
                                    count.notify();
                            }
                            logentry.free();
                        }
                        
                        @Override
                        public void failed(BabuDBException error, Object context) {
                            Logging.logError(Logging.LEVEL_ERROR, stage, error);
                            synchronized (count) {
                                count.set(-1);
                                count.notify();
                            }
                            logentry.free();
                        }
                    });
                } finally {
                    this.checksum.reset();
                }  
            }
            
            // block until all inserts are finished
            synchronized (count) {
                while (count.get() > 0)
                    count.wait();
            }
            
            // at least one insert failed
            if (count.get() == -1) 
                throw new LogEntryException("At least one insert could not be" +
                		" proceeded.");
 
            finish();
            if (Thread.interrupted()) 
                throw new InterruptedException("Replication was interrupted" +
                		" after executing a replicaOperation.");
        } catch (ErrorCodeException e) {
            // server-side error
            throw new ConnectionLostException(e.getCode());
        } catch (IOException ioe) {
            // failure on transmission (connection lost or request timed out)
            throw new ConnectionLostException(ioe.getMessage(), ErrorCode.BUSY);
        } catch (LogEntryException lee) {
            // decoding failed --> retry with new range
            Logging.logError(Logging.LEVEL_WARN, this, lee);
            finish();
        } catch (BabuDBException be) {
            // the insert failed due an DB error
            Logging.logError(Logging.LEVEL_WARN, this, be);
            stage.missing = new Range(stage.lastInserted, stage.missing.end);
            stage.setLogic(LOAD, be.getMessage());
        } finally {
            if (logEntries!=null) {
                for (ReusableBuffer buf : logEntries) {
                    if (buf != null) BufferPool.free(buf);
                }
            }
        }
    }
    
    /**
     * Method to decide which logic shall be used next.
     */
    private void finish() {
        LSN next = new LSN (stage.lastInserted.getViewId(),
                            stage.lastInserted.getSequenceNo() + 1L);
        
        // we are still missing some entries (the request was too large)
        // update the missing entries
        if (next.compareTo(stage.missing.end) < 0) {
            stage.missing = new Range(stage.lastInserted, stage.missing.end); 

         // all went fine --> back to basic
        } else {
            stage.missing = null;
            stage.setLogic(BASIC, "Request went fine, we can go on with" +
                        " the basicLogic.");
        }
    }
}