/*
 * Copyright (c) 2009, Jan Stender, Bjoern Kolbeck, Mikael Hoegqvist,
 *                     Felix Hupfeld, Felix Langner, Zuse Institute Berlin
 * 
 * Licensed under the BSD License, see LICENSE file for details.
 * 
 */
package org.xtreemfs.babudb.lsmdb;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xtreemfs.babudb.BabuDB;
import org.xtreemfs.babudb.BabuDBException;
import org.xtreemfs.babudb.BabuDBException.ErrorCode;
import org.xtreemfs.babudb.index.ByteRangeComparator;
import org.xtreemfs.babudb.replication.DirectFileIO;
import org.xtreemfs.include.common.logging.Logging;

/**
 * <p>
 * Operations to manipulate the DB-config-file.
 * </p>
 * 
 * @author flangner
 * @since 09/02/2009
 */

public class DBConfig {
    
    private final BabuDB dbs;
    
    private final File configFile;

    public DBConfig(BabuDB dbs) throws BabuDBException {
        this.dbs = dbs;
        this.configFile = new File(this.dbs.getConfig().getBaseDir() + this.dbs.getConfig().getDbCfgFile());
        load();
    }
    
    /**
     * Loads the configuration and each database from disk.
     * 
     * @throws BabuDBException
     */
    public void load() throws BabuDBException {
        DatabaseManagerImpl dbman = (DatabaseManagerImpl) dbs.getDatabaseManager();
        assert (dbman != null) : "The DatabaseManager is not available!";
        
        ObjectInputStream ois = null;
        try {
            if (configFile.exists()) {
                ois = new ObjectInputStream(new FileInputStream(configFile));
                final int dbFormatVer = ois.readInt();
                if (dbFormatVer != BabuDB.BABUDB_DB_FORMAT_VERSION) {
                    throw new BabuDBException(ErrorCode.IO_ERROR, "on-disk format (version " + dbFormatVer
                        + ") is incompatible with this BabuDB release " + "(uses on-disk format version "
                        + BabuDB.BABUDB_DB_FORMAT_VERSION + ")");
                }
                final int numDB = ois.readInt();
                dbman.nextDbId = 
                    ois.readInt();
                for (int i = 0; i < numDB; i++) {
                    Logging.logMessage(Logging.LEVEL_DEBUG, this, "loading DB...");
                    final String dbName = (String) ois.readObject();
                    final int dbId = ois.readInt();
                    final int numIndex = ois.readInt();
                    ByteRangeComparator[] comps = new ByteRangeComparator[numIndex];
                    for (int idx = 0; idx < numIndex; idx++) {
                        final String className = (String) ois.readObject();
                        ByteRangeComparator comp = dbman.compInstances.get(className);
                        if (comp == null) {
                            Class<?> clazz = Class.forName(className);
                            comp = (ByteRangeComparator) clazz.newInstance();
                            dbman.compInstances.put(className, comp);
                        }
                        
                        assert (comp != null);
                        comps[idx] = comp;
                    }
                    
                    Database db = new DatabaseImpl(this.dbs, 
                            new LSMDatabase(dbName, dbId, this.dbs.getConfig()
                            .getBaseDir()
                        + dbName + File.separatorChar, numIndex, true, comps));
                    dbman.dbsById.put(dbId, db);
                    dbman.dbsByName.put(dbName, db);
                    Logging.logMessage(Logging.LEVEL_DEBUG, this, "loaded DB " + 
                            dbName + " successfully.");
                }
            }
            
        } catch (InstantiationException ex) {
            throw new BabuDBException(ErrorCode.IO_ERROR, "cannot instantiate comparator", ex);
        } catch (IllegalAccessException ex) {
            throw new BabuDBException(ErrorCode.IO_ERROR, "cannot instantiate comparator", ex);
        } catch (IOException ex) {
            throw new BabuDBException(ErrorCode.IO_ERROR,
                "cannot load database config, check path and access rights", ex);
        } catch (ClassNotFoundException ex) {
            throw new BabuDBException(ErrorCode.IO_ERROR,
                "cannot load database config, config file might be corrupted", ex);
        } catch (ClassCastException ex) {
            throw new BabuDBException(ErrorCode.IO_ERROR,
                "cannot load database config, config file might be corrupted", ex);
        } finally {
            if (ois != null)
                try {
                    ois.close();
                } catch (IOException e) {
                    /* who cares? */
                }
        }
    }
    
    /**
     * saves the current database config to disk
     * 
     * @throws BabuDBException
     */
    public void save() throws BabuDBException {
        DatabaseManagerImpl dbman = (DatabaseManagerImpl) dbs.getDatabaseManager();

        synchronized (dbman.getDBModificationLock()) {
            try {
                FileOutputStream fos = new FileOutputStream(dbs.getConfig().getBaseDir()
                    + dbs.getConfig().getDbCfgFile() + ".in_progress");
                ObjectOutputStream oos = new ObjectOutputStream(fos);
                oos.writeInt(BabuDB.BABUDB_DB_FORMAT_VERSION);
                oos.writeInt(dbman.dbsById.size());
                oos.writeInt(dbman.nextDbId);
                for (int dbId : dbman.dbsById.keySet()) {
                    LSMDatabase db = ((DatabaseImpl) dbman.dbsById.get(dbId)).getLSMDB();
                    oos.writeObject(db.getDatabaseName());
                    oos.writeInt(dbId);
                    oos.writeInt(db.getIndexCount());
                    String[] compClasses = db.getComparatorClassNames();
                    for (int i = 0; i < db.getIndexCount(); i++) {
                        oos.writeObject(compClasses[i]);
                    }
                }
                
                oos.flush();
                fos.flush();
                fos.getFD().sync();
                oos.close();
                File f = new File(dbs.getConfig().getBaseDir() + dbs.getConfig().getDbCfgFile()
                    + ".in_progress");
                f.renameTo(new File(dbs.getConfig().getBaseDir() + dbs.getConfig().getDbCfgFile()));
            } catch (IOException ex) {
                throw new BabuDBException(ErrorCode.IO_ERROR, "unable to save database configuration", ex);
            }
            
        }
    }
    
    /**
     * <p>
     * Saves the currently used config-file.
     * Creates a copy of the currently used config-file, 
     * corresponding to the latest checkpoint, 
     * identified by the latest {@link LSN}.
     * </p>
     * 
     * @param lsn
     * 
     * @throws BabuDBException 
     */
    public void checkpoint(LSN lsn) throws BabuDBException {
        save();
        if (configFile.exists()) {
            File checkpoint = new File(dbs.getConfig().getBaseDir() + 
                    toDBConfigFileName(lsn, dbs.getConfig().getDbCfgFile()));
            try {
                checkpoint.createNewFile();
                DirectFileIO.copyFile(configFile, checkpoint);
            } catch (IOException io) {
                throw new BabuDBException(ErrorCode.IO_ERROR,"Could not create"+
                	" a checkpoint of the DB-config-file: "+io.getMessage());
            }
            cleanupOldCheckpoints(lsn);
        } else {
            throw new BabuDBException(ErrorCode.IO_ERROR, 
                    "No config-file available at the moment.");
        }
    }
    
    /**
     * Removes DB-config-files of out-dated checkpoints.
     * 
     * @param lsn
     */
    private void cleanupOldCheckpoints(LSN lsn) {
        File f = new File(dbs.getConfig().getBaseDir());
        String[] confs = f.list(new FilenameFilter() {
            
            public boolean accept(File dir, String name) {
                return name.endsWith("."+dbs.getConfig().getDbCfgFile());
            }
        });
        
        for (String conf : confs) {
            if (fromDBConfigFileName(conf,dbs.getConfig().getDbCfgFile())
                    .compareTo(lsn) < 0) {
                Logging.logMessage(Logging.LEVEL_DEBUG, this, "deleting old " +
                		"DB config-file: " + conf);
                f = new File(dbs.getConfig().getBaseDir() + conf);
                f.delete();
            }
        }
    }
    
    /**
     * 
     * @param configFileName
     * @return the {@link LSN} of the configFileName.
     */
    public static LSN fromDBConfigFileName(String configFileName, 
            String basicDBConfigFileName) {
        Pattern p = Pattern.compile("(\\d+)\\.(\\d+)\\."+basicDBConfigFileName);
        
        Matcher m = p.matcher(configFileName);
        m.matches();
        String tmp = m.group(1);
        int viewId = Integer.valueOf(tmp);
        tmp = m.group(2);
        int seqNo = Integer.valueOf(tmp);
        return new LSN(viewId, seqNo);
    }
    
    /**
     * 
     * @param basicDBConfigFileName - the basic name.
     * @param lsn
     * @return the DBconfigFile to the given {@link LSN}.
     */
    public static String toDBConfigFileName(LSN lsn, 
            String basicDBConfigFileName) {
        
        return lsn.getViewId()+"."+lsn.getSequenceNo()+"."
                        +basicDBConfigFileName;
    }
}
