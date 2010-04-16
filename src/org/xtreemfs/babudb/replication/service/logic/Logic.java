/*
 * Copyright (c) 2009, Jan Stender, Bjoern Kolbeck, Mikael Hoegqvist,
 *                     Felix Hupfeld, Felix Langner, Zuse Institute Berlin
 * 
 * Licensed under the BSD License, see LICENSE file for details.
 * 
 */
package org.xtreemfs.babudb.replication.service.logic;

import org.xtreemfs.babudb.replication.BabuDBInterface;
import org.xtreemfs.babudb.replication.service.Pacemaker;
import org.xtreemfs.babudb.replication.service.ReplicationStage;
import org.xtreemfs.babudb.replication.service.SlaveView;
import org.xtreemfs.babudb.replication.service.ReplicationStage.ConnectionLostException;
import org.xtreemfs.babudb.replication.transmission.FileIOInterface;

/**
 * Interface for replication-behavior classes.
 * 
 * @author flangner
 * @since 06/08/2009
 */

public abstract class Logic {
    
    protected final ReplicationStage stage;

    protected final Pacemaker        pacemaker;
    
    protected final SlaveView        slaveView;
    
    protected final BabuDBInterface  babuInterface;
    
    protected final FileIOInterface  fileIO;
    
    public Logic(ReplicationStage stage, Pacemaker pacemaker, 
            SlaveView slaveView, FileIOInterface fileIO, 
            BabuDBInterface babuInterface) {
        
        this.pacemaker = pacemaker;
        this.slaveView = slaveView;
        this.babuInterface = babuInterface;
        this.fileIO = fileIO;
        this.stage = stage;
    }
    
    /**
     * @return unique id, identifying the logic.
     */
    public abstract LogicID getId();
    
    /**
     * Function to execute, if logic is needed.
     * 
     * @throws ConnectionLostException if the connection to the participant is lost.
     * @throws InterruptedException if the execution was interrupted.
     */
    public abstract void run() throws ConnectionLostException, InterruptedException;
}