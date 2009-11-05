/*
 * Copyright (c) 2009, Jan Stender, Bjoern Kolbeck, Mikael Hoegqvist,
 *                     Felix Hupfeld, Felix Langner, Zuse Institute Berlin
 * 
 * Licensed under the BSD License, see LICENSE file for details.
 * 
 */

package org.xtreemfs.babudb.lsmdb;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Iterator;
import java.util.Map.Entry;

import org.xtreemfs.babudb.BabuDB;
import org.xtreemfs.babudb.BabuDBException;
import org.xtreemfs.babudb.BabuDBRequestListener;
import org.xtreemfs.babudb.UserDefinedLookup;
import org.xtreemfs.babudb.BabuDBException.ErrorCode;
import org.xtreemfs.babudb.index.ByteRangeComparator;
import org.xtreemfs.babudb.index.LSMTree;
import org.xtreemfs.babudb.log.DiskLogger;
import org.xtreemfs.babudb.log.LogEntry;
import org.xtreemfs.babudb.log.SyncListener;
import org.xtreemfs.babudb.lsmdb.InsertRecordGroup.InsertRecord;
import org.xtreemfs.babudb.snapshots.SnapshotConfig;
import org.xtreemfs.include.common.buffer.BufferPool;
import org.xtreemfs.include.common.buffer.ReusableBuffer;
import org.xtreemfs.include.common.config.ReplicationConfig;
import org.xtreemfs.include.common.logging.Logging;

public class DatabaseImpl implements Database {
    
    /**
     * @author bjko
     * 
     */
    public static class AsyncResult {
        
        public boolean                         done = false;
        
        public byte[]                          value;
        
        public Object                          udlresult;
        
        public Iterator<Entry<byte[], byte[]>> iterator;
        
        public BabuDBException                 error;
        
    }
    
    private BabuDB      dbs;
    
    private LSMDatabase lsmDB;
    
    /**
     * set true, if the insert should be established in memory, which
     * can cause inconsistencies, or false if not.
     */
    private final boolean optimistic;
    
    /**
     * Creates a new Database.
     * 
     * @param lsmDB
     *            the underlying LSM database
     */
    public DatabaseImpl(BabuDB master, LSMDatabase lsmDB) {
        this.dbs = master;
        this.lsmDB = lsmDB;
        if (dbs.getConfig() instanceof ReplicationConfig)
            this.optimistic = ((ReplicationConfig) dbs.getConfig()).isOptimistic();
        else
            this.optimistic = false;
    }
    
    @Override
    public BabuDBInsertGroup createInsertGroup() throws BabuDBException {
        dbs.slaveCheck();
        
        return new BabuDBInsertGroup(lsmDB);
    }
    
    @Override
    public void syncSingleInsert(int indexId, byte[] key, byte[] value) throws BabuDBException {
        dbs.slaveCheck();
        
        BabuDBInsertGroup irec = new BabuDBInsertGroup(lsmDB);
        irec.addInsert(indexId, key, value);
        
        final AsyncResult result = new AsyncResult();
        
        asyncInsert(irec, new BabuDBRequestListener() {
            
            public void insertFinished(Object context) {
                synchronized (result) {
                    result.done = true;
                    result.notify();
                }
            }
            
            public void lookupFinished(Object context, byte[] value) {
            }
            
            public void prefixLookupFinished(Object context, Iterator<Entry<byte[], byte[]>> iterator) {
            }
            
            public void requestFailed(Object context, BabuDBException error) {
                synchronized (result) {
                    result.done = true;
                    result.error = error;
                    result.notify();
                }
            }
            
            public void userDefinedLookupFinished(Object context, Object result) {
            }
        }, null);
        
        synchronized (result) {
            try {
                if (!result.done) {
                    result.wait();
                }
            } catch (InterruptedException ex) {
            }
        }
        if (result.error != null) {
            throw result.error;
        }
    }
    
    @Override
    public void syncInsert(BabuDBInsertGroup irg) throws BabuDBException {
        dbs.slaveCheck();
        
        final AsyncResult result = new AsyncResult();
        
        asyncInsert(irg, new BabuDBRequestListener() {
            
            public void insertFinished(Object context) {
                synchronized (result) {
                    result.done = true;
                    result.notify();
                }
            }
            
            public void lookupFinished(Object context, byte[] value) {
            }
            
            public void prefixLookupFinished(Object context, Iterator<Entry<byte[], byte[]>> iterator) {
            }
            
            public void requestFailed(Object context, BabuDBException error) {
                synchronized (result) {
                    result.done = true;
                    result.error = error;
                    result.notify();
                }
            }
            
            public void userDefinedLookupFinished(Object context, Object result) {
            }
        }, null);
        synchronized (result) {
            try {
                if (!result.done) {
                    result.wait();
                }
            } catch (InterruptedException ex) {
            }
        }
        if (result.error != null) {
            throw result.error;
        }
    }
    
    @Override
    public void asyncInsert(BabuDBInsertGroup ig, BabuDBRequestListener listener, Object context)
        throws BabuDBException {
        dbs.slaveCheck();
        
        final InsertRecordGroup ins = ig.getRecord();
        final int dbId = ins.getDatabaseId();
        
        LSMDBWorker w = dbs.getWorker(dbId);
        if (Logging.isNotice()) {
            Logging.logMessage(Logging.LEVEL_NOTICE, this, "insert request is sent to worker #" + dbId
                % dbs.getWorkerCount());
        }
        
        try {
            w.addRequest(new LSMDBRequest(lsmDB, listener, ins, context));
        } catch (InterruptedException ex) {
            throw new BabuDBException(ErrorCode.INTERNAL_ERROR, "operation was interrupted", ex);
        }
    }
    
    
    /*
     * (non-Javadoc)
     * @see org.xtreemfs.babudb.lsmdb.Database#directInsert(org.xtreemfs.babudb.lsmdb.BabuDBInsertGroup, org.xtreemfs.babudb.log.SyncListener)
     */
    @Override
    public void directInsert(BabuDBInsertGroup irg) throws BabuDBException {
        dbs.slaveCheck();
        
        if (!optimistic) {
            syncDirectInsert(irg);
        } else {
            directInsert(irg, this.dbs.getGlobalSyncListener(), optimistic);
        }
    }
    
    public void syncDirectInsert(BabuDBInsertGroup irg) throws BabuDBException {
        final AsyncResult result = new AsyncResult();
        
        directInsert(irg, new SyncListener() {
            
            public void synced(LogEntry entry) {
                entry.free();
                
                synchronized (result) {
                    result.done = true;
                    result.notifyAll();
                }
            }
            
            public void failed(LogEntry entry, Exception ex) {
                entry.free();
                
                synchronized (result) {
                    result.done = true;
                    result.error = new BabuDBException(ErrorCode.IO_ERROR,
                        "could not execute insert because of IO problem", ex);
                    result.notifyAll();
                }
            }
        }, false);
        
        
        synchronized (result) {
            if (!result.done) {
                try {
                    result.wait();
                } catch (InterruptedException ex) {
                    throw new BabuDBException(ErrorCode.INTERNAL_ERROR, 
                            "cannot write update to disk log", ex);
                }
            }
        }
        
        if (result.error != null) {
            throw result.error;
        }
        
        for (InsertRecord ir : irg.getRecord().getInserts()) {
            final LSMTree index = lsmDB.getIndex(ir.getIndexId());
            if (ir.getValue() != null) {
                index.insert(ir.getKey(), ir.getValue());
            } else {
                index.delete(ir.getKey());
            }
        }
    }
    
    private void directInsert(BabuDBInsertGroup irg, SyncListener listener, 
            boolean optimistic) throws BabuDBException {
        
        final int numIndices = lsmDB.getIndexCount();
        
        for (InsertRecord ir : irg.getRecord().getInserts()) {
            if ((ir.getIndexId() >= numIndices) || (ir.getIndexId() < 0)) {
                throw new BabuDBException(ErrorCode.NO_SUCH_INDEX, "index " + 
                        ir.getIndexId() + " does not exist");
            }
        }
        
        int size = irg.getRecord().getSize();
        ReusableBuffer buf = BufferPool.allocate(size);
        irg.getRecord().serialize(buf);
        buf.flip();
        
        LogEntry e = new LogEntry(buf, listener, LogEntry.PAYLOAD_TYPE_INSERT);
        
        try {
            dbs.getLogger().append(e);
        } catch (InterruptedException ex) {
            throw new BabuDBException(ErrorCode.INTERNAL_ERROR, 
                    "cannot write update to disk log", ex);
        }
        
        if (optimistic) {
            for (InsertRecord ir : irg.getRecord().getInserts()) {
                final LSMTree index = lsmDB.getIndex(ir.getIndexId());
                if (ir.getValue() != null) {
                    index.insert(ir.getKey(), ir.getValue());
                } else {
                    index.delete(ir.getKey());
                }
            }
        }
    }
    
    @Override
    public byte[] syncLookup(int indexId, byte[] key) throws BabuDBException {
        dbs.slaveCheck();
        
        final AsyncResult result = new AsyncResult();
        
        asyncLookup(indexId, key, new BabuDBRequestListener() {
            
            public void insertFinished(Object context) {
            }
            
            public void lookupFinished(Object context, byte[] value) {
                synchronized (result) {
                    result.done = true;
                    result.value = value;
                    result.notify();
                }
            }
            
            public void prefixLookupFinished(Object context, Iterator<Entry<byte[], byte[]>> iterator) {
            }
            
            public void requestFailed(Object context, BabuDBException error) {
                synchronized (result) {
                    result.done = true;
                    result.error = error;
                    result.notify();
                }
            }
            
            public void userDefinedLookupFinished(Object context, Object result) {
            }
        }, null);
        
        synchronized (result) {
            try {
                if (!result.done) {
                    result.wait();
                }
            } catch (InterruptedException ex) {
            }
        }
        if (result.error != null) {
            throw result.error;
        }
        return result.value;
    }
    
    @Override
    public Object syncUserDefinedLookup(UserDefinedLookup udl) throws BabuDBException {
        dbs.slaveCheck();
        
        final AsyncResult result = new AsyncResult();
        
        asyncUserDefinedLookup(new BabuDBRequestListener() {
            
            public void insertFinished(Object context) {
            }
            
            public void lookupFinished(Object context, byte[] value) {
            }
            
            public void prefixLookupFinished(Object context, Iterator<Entry<byte[], byte[]>> iterator) {
            }
            
            public void requestFailed(Object context, BabuDBException error) {
                synchronized (result) {
                    result.done = true;
                    result.error = error;
                    result.notify();
                }
            }
            
            public void userDefinedLookupFinished(Object context, Object result2) {
                synchronized (result) {
                    result.done = true;
                    result.udlresult = result2;
                    result.notify();
                }
            }
        }, udl, null);
        
        synchronized (result) {
            try {
                if (!result.done) {
                    result.wait();
                }
            } catch (InterruptedException ex) {
            }
        }
        if (result.error != null) {
            throw result.error;
        }
        return result.udlresult;
    }
    
    @Override
    public Iterator<Entry<byte[], byte[]>> syncPrefixLookup(int indexId, byte[] key) throws BabuDBException {
        dbs.slaveCheck();
        
        final AsyncResult result = new AsyncResult();
        
        asyncPrefixLookup(indexId, key, new BabuDBRequestListener() {
            
            public void insertFinished(Object context) {
            }
            
            public void lookupFinished(Object context, byte[] value) {
            }
            
            public void prefixLookupFinished(Object context, Iterator<Entry<byte[], byte[]>> iterator) {
                synchronized (result) {
                    result.done = true;
                    result.iterator = iterator;
                    result.notify();
                }
            }
            
            public void requestFailed(Object context, BabuDBException error) {
                synchronized (result) {
                    result.done = true;
                    result.error = error;
                    result.notify();
                }
            }
            
            public void userDefinedLookupFinished(Object context, Object result) {
            }
        }, null);
        
        synchronized (result) {
            try {
                if (!result.done) {
                    result.wait();
                }
            } catch (InterruptedException ex) {
            }
        }
        if (result.error != null) {
            throw result.error;
        }
        return result.iterator;
    }
    
    @Override
    public void asyncUserDefinedLookup(BabuDBRequestListener listener, UserDefinedLookup udl, Object context)
        throws BabuDBException {
        dbs.slaveCheck();
        
        LSMDBWorker w = dbs.getWorker(lsmDB.getDatabaseId());
        if (Logging.isNotice()) {
            Logging.logMessage(Logging.LEVEL_NOTICE, this, "udl request is sent to worker #"
                + lsmDB.getDatabaseId() % dbs.getWorkerCount());
        }
        
        try {
            w.addRequest(new LSMDBRequest(lsmDB, listener, udl, context));
        } catch (InterruptedException ex) {
            throw new BabuDBException(ErrorCode.INTERNAL_ERROR, "operation was interrupted", ex);
        }
    }
    
    @Override
    public void asyncLookup(int indexId, byte[] key, BabuDBRequestListener listener, Object context)
        throws BabuDBException {
        dbs.slaveCheck();
        
        LSMDBWorker w = dbs.getWorker(lsmDB.getDatabaseId());
        if (Logging.isNotice()) {
            Logging.logMessage(Logging.LEVEL_NOTICE, this, "lookup request is sent to worker #"
                + lsmDB.getDatabaseId() % dbs.getWorkerCount());
        }
        
        try {
            w.addRequest(new LSMDBRequest(lsmDB, indexId, listener, key, false, context));
        } catch (InterruptedException ex) {
            throw new BabuDBException(ErrorCode.INTERNAL_ERROR, "operation was interrupted", ex);
        }
    }
    
    @Override
    public void asyncPrefixLookup(int indexId, byte[] key, BabuDBRequestListener listener, Object context)
        throws BabuDBException {
        dbs.slaveCheck();
        
        LSMDBWorker w = dbs.getWorker(lsmDB.getDatabaseId());
        if (Logging.isNotice()) {
            Logging.logMessage(Logging.LEVEL_NOTICE, this, "lookup request is sent to worker #"
                + lsmDB.getDatabaseId() % dbs.getWorkerCount());
        }
        
        try {
            w.addRequest(new LSMDBRequest(lsmDB, indexId, listener, key, true, context));
        } catch (InterruptedException ex) {
            throw new BabuDBException(ErrorCode.INTERNAL_ERROR, "operation was interrupted", ex);
        }
    }
    
    @Override
    public byte[] directLookup(int indexId, byte[] key) throws BabuDBException {
        
        if ((indexId >= lsmDB.getIndexCount()) || (indexId < 0)) {
            throw new BabuDBException(ErrorCode.NO_SUCH_INDEX, "index does not exist");
        }
        return lsmDB.getIndex(indexId).lookup(key);
    }
    
    public byte[] directLookup(int indexId, int snapId, byte[] key) throws BabuDBException {
        dbs.slaveCheck();
        
        if ((indexId >= lsmDB.getIndexCount()) || (indexId < 0)) {
            throw new BabuDBException(ErrorCode.NO_SUCH_INDEX, "index does not exist");
        }
        return lsmDB.getIndex(indexId).lookup(key, snapId);
    }
    
    @Override
    public Iterator<Entry<byte[], byte[]>> directPrefixLookup(int indexId, byte[] key) throws BabuDBException {
        
        if ((indexId >= lsmDB.getIndexCount()) || (indexId < 0)) {
            throw new BabuDBException(ErrorCode.NO_SUCH_INDEX, "index does not exist");
        }
        return lsmDB.getIndex(indexId).prefixLookup(key);
    }
    
    @Override
    public Iterator<Entry<byte[], byte[]>> directReversePrefixLookup(int indexId, byte[] key)
        throws BabuDBException {
        dbs.slaveCheck();
        
        if ((indexId >= lsmDB.getIndexCount()) || (indexId < 0)) {
            throw new BabuDBException(ErrorCode.NO_SUCH_INDEX, "index does not exist");
        }
        return lsmDB.getIndex(indexId).prefixLookup(key, false);
    }
    
    public Iterator<Entry<byte[], byte[]>> directPrefixLookup(int indexId, int snapId, byte[] key,
        boolean ascending) throws BabuDBException {
        dbs.slaveCheck();
        
        if ((indexId >= lsmDB.getIndexCount()) || (indexId < 0)) {
            throw new BabuDBException(ErrorCode.NO_SUCH_INDEX, "index does not exist");
        }
        return lsmDB.getIndex(indexId).prefixLookup(key, snapId, ascending);
    }
    
    /**
     * Returns the underlying LSM database implementation.
     * 
     * @return the LSM database
     */
    public LSMDatabase getLSMDB() {
        return lsmDB;
    }
    
    @Override
    public void shutdown() throws BabuDBException {
        
        try {
            for (int index = 0; index < lsmDB.getIndexCount(); index++)
                lsmDB.getIndex(index).destroy();
        } catch (IOException exc) {
            throw new BabuDBException(ErrorCode.IO_ERROR, "", exc);
        }
    }
    
    @Override
    public ByteRangeComparator[] getComparators() {
        return lsmDB.getComparators();
    }
    
    @Override
    public String getName() {
        return lsmDB.getDatabaseName();
    }
    
    /**
     * Creates an in-memory snapshot of all indices in a single database and
     * writes the snapshot to disk. Eludes the slave-check.
     * 
     * NOTE: this method should only be invoked by the replication
     * 
     * @param destDB
     *            - the name of the destination DB name.
     * 
     * @throws BabuDBException
     *             if the checkpoint was not successful
     * @throws InterruptedException
     */
    public void proceedSnapshot(String destDB) throws BabuDBException, InterruptedException {
        int[] ids;
        try {
            // critical block...
            dbs.getLogger().lockLogger();
            ids = lsmDB.createSnapshot();
        } finally {
            if (dbs.getLogger().hasLock())
                dbs.getLogger().unlockLogger();
        }
        
        File dbDir = new File(dbs.getConfig().getBaseDir() + destDB);
        if (!dbDir.exists()) {
            dbDir.mkdirs();
        }
        
        try {
            LSN lsn = lsmDB.getOndiskLSN();
            lsmDB.writeSnapshot(dbs.getConfig().getBaseDir() + destDB + File.
                    separatorChar, ids, lsn.getViewId(), lsn.getSequenceNo());
        } catch (IOException ex) {
            throw new BabuDBException(ErrorCode.IO_ERROR, "cannot write snapshot: " + ex, ex);
        }
    }
    
    /**
     * Creates an in-memory snapshot of all indices in a single database. The
     * snapshot will be discarded when the system is restarted.
     * 
     * NOTE: this method should only be invoked by the framework
     * 
     * @throws BabuDBException
     *             if isSlave_check succeeded
     * @throws InterruptedException
     * @return an array with the snapshot ID for each index in the database
     */
    public int[] createSnapshot() throws BabuDBException, InterruptedException {
        dbs.slaveCheck();
        
        int[] result = null;
        try {
            // critical block...
            dbs.getLogger().lockLogger();
            result = proceedCreateSnapshot();
        } finally {
            dbs.getLogger().unlockLogger();
        }
        return result;
    }

    /**
     * Creates an in-memory snapshot of all indices in a single database. The
     * snapshot will be discarded when the system is restarted.
     * This Operation comes without slave-protection.
     * The {@link DiskLogger} has to be locked before executing this method. 
     * 
     * NOTE: this method should only be invoked by the framework
     * 
     * @return an array with the snapshot ID for each index in the database
     */
    public int[] proceedCreateSnapshot() {       
        return lsmDB.createSnapshot();
    }

    
    /**
     * Creates an in-memory snapshot of a given set of indices in a single
     * database. The snapshot will be restored when the system is restarted.
     * 
     * NOTE: this method should only be invoked by the framework
     * 
     * @throws BabuDBException
     *             if the checkpoint was not successful
     * @throws InterruptedException
     * @return an array with the snapshot ID for each index in the database
     */
    public int[] createSnapshot(SnapshotConfig snap, boolean appendLogEntry) throws BabuDBException,
        InterruptedException {
        dbs.slaveCheck();
        
        if (appendLogEntry) {
            
            // serialize the snapshot configuration
            ReusableBuffer buf = null;
            try {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                ObjectOutputStream oout = new ObjectOutputStream(bout);
                oout.writeInt(lsmDB.getDatabaseId());
                oout.writeObject(snap);
                buf = ReusableBuffer.wrap(bout.toByteArray());
                oout.close();
            } catch (IOException exc) {
                throw new BabuDBException(ErrorCode.IO_ERROR, 
                        "could not serialize snapshot configuration: " + 
                        snap.getClass(), exc);
            }
            
            DatabaseManagerImpl.metaInsert(LogEntry.PAYLOAD_TYPE_SNAP, buf, 
                    dbs.getLogger());
        }
        
        // critical block...
        if (dbs.getLogger() != null)
            dbs.getLogger().lockLogger();
        
        // create the snapshot
        int[] result = lsmDB.createSnapshot(snap.getIndices());
        
        if (dbs.getLogger() != null)
            dbs.getLogger().unlockLogger();
        
        return result;
        
    }
    
    /**
     * Writes a snapshot to disk.
     * 
     * NOTE: this method should only be invoked by the framework
     * 
     * @param snapIds
     *            the snapshot IDs obtained from createSnapshot
     * @param directory
     *            the directory in which the snapshots are written
     * @param cfg
     *            the snapshot configuration
     * @throws BabuDBException
     *             if the snapshot cannot be written,
     *             or isSlave_check was positive
     */
    public void writeSnapshot(int[] snapIds, String directory, SnapshotConfig cfg) throws BabuDBException {
        dbs.slaveCheck();
        
        proceedWriteSnapshot(snapIds, directory, cfg);
    }
    
    /**
     * Writes a snapshot to disk. Without isSlave protection.
     * 
     * NOTE: this method should only be invoked by the framework
     * 
     * @param snapIds
     *            the snapshot IDs obtained from createSnapshot
     * @param directory
     *            the directory in which the snapshots are written
     * @param cfg
     *            the snapshot configuration
     * @throws BabuDBException
     *             if the snapshot cannot be written
     */
    public void proceedWriteSnapshot(int[] snapIds, String directory, SnapshotConfig cfg) throws BabuDBException {
        try {
            lsmDB.writeSnapshot(directory, snapIds, cfg);
        } catch (IOException ex) {
            throw new BabuDBException(ErrorCode.IO_ERROR, "cannot write snapshot: " + ex, ex);
        }
    }
    
    /**
     * Writes the snapshots to disk.
     * 
     * @param viewId
     *            current viewId (i.e. of the last write)
     * @param sequenceNo
     *            current sequenceNo (i.e. of the last write)
     * @param snapIds
     *            the snapshot Ids (obtained via createSnapshot).
     * @throws BabuDBException
     *             if a snapshot cannot be written,
     *             or BabuDB is running in slave-mode.
     */
    public void writeSnapshot(int viewId, long sequenceNo, int[] snapIds) throws BabuDBException {
        dbs.slaveCheck();
        
        proceedWriteSnapshot(viewId, sequenceNo, snapIds);
    }
    
    /**
     * Writes the snapshots to disk. Without slave-protection.
     * 
     * @param viewId
     *            current viewId (i.e. of the last write)
     * @param sequenceNo
     *            current sequenceNo (i.e. of the last write)
     * @param snapIds
     *            the snapshot Ids (obtained via createSnapshot).
     * @throws BabuDBException
     *             if a snapshot cannot be written
     */
    public void proceedWriteSnapshot(int viewId, long sequenceNo, int[] snapIds) throws BabuDBException {
        try {
            lsmDB.writeSnapshot(viewId, sequenceNo, snapIds);
        } catch (IOException ex) {
            throw new BabuDBException(ErrorCode.IO_ERROR, "cannot write snapshot: " + ex, ex);
        }
        
    }
    
    /**
     * Links the indices to the latest on-disk snapshot, cleans up any
     * unnecessary in-memory and on-disk data
     * 
     * @param viewId
     *            the viewId of the snapshot
     * @param sequenceNo
     *            the sequenceNo of the snaphot
     * @throws BabuDBException
     *             if snapshots cannot be cleaned up
     */
    public void cleanupSnapshot(final int viewId, final long sequenceNo) throws BabuDBException {
        dbs.slaveCheck();
        
        proceedCleanupSnapshot(viewId, sequenceNo);
    }
    
    /**
     * Links the indices to the latest on-disk snapshot, cleans up any
     * unnecessary in-memory and on-disk data. Without slave-protection.
     * 
     * @param viewId
     *            the viewId of the snapshot
     * @param sequenceNo
     *            the sequenceNo of the snaphot
     * @throws BabuDBException
     *             if snapshots cannot be cleaned up
     */
    public void proceedCleanupSnapshot(final int viewId, final long sequenceNo) throws BabuDBException {
        try {
            lsmDB.cleanupSnapshot(viewId, sequenceNo);
        } catch (IOException ex) {
            throw new BabuDBException(ErrorCode.IO_ERROR, "cannot clean up: " + ex, ex);
        }
    }

    /**
     * <p>
     * Replaces the currently used {@link LSMDatabase} with the given one.
     * <br>
     * Be really careful with this operation. 
     * {@link LSMDBRequest}s might get lost.
     * </p> 
     * 
     * @param lsmDatabase
     */
    public void reset(LSMDatabase lsmDatabase) {
        this.lsmDB = lsmDatabase;
    }
}
