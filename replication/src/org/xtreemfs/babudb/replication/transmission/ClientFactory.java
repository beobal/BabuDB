/*
 * Copyright (c) 2010 - 2011, Jan Stender, Bjoern Kolbeck, Mikael Hoegqvist,
 *                     Felix Hupfeld, Felix Langner, Zuse Institute Berlin
 * 
 * Licensed under the BSD License, see LICENSE file for details.
 * 
 */
package org.xtreemfs.babudb.replication.transmission;

import java.net.InetSocketAddress;

import org.xtreemfs.babudb.pbrpc.ReplicationServiceClient;
import org.xtreemfs.babudb.replication.RemoteAccessClient;

/**
 * Interface for {@link ReplicationServiceClient}-generating objects.
 * 
 * @author flangner
 * @since 04/13/2010
 */
public interface ClientFactory {

    /**
     * @param receiver
     * @return the {@link RemoteClientAdapter}, an abstraction from the 
     *         underlying RPC architecture.
     */
    public PBRPCClientAdapter getClient(InetSocketAddress receiver);
    
    /**
     * 
     * @return a generic proxy-client instance as abstraction from the 
     *         underlying RPC architecture.
     */
    public RemoteAccessClient getProxyClient();
}