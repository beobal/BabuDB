package org.xtreemfs.babudb.interfaces.ReplicationInterface;

import org.xtreemfs.babudb.*;
import org.xtreemfs.babudb.interfaces.*;
import java.util.HashMap;
import org.xtreemfs.babudb.interfaces.utils.*;
import org.xtreemfs.include.foundation.oncrpc.utils.ONCRPCBufferWriter;
import org.xtreemfs.include.common.buffer.ReusableBuffer;




public class ProtocolException extends org.xtreemfs.babudb.interfaces.utils.ONCRPCException
{
    public static final int TAG = 1100;

    
    public ProtocolException() { accept_stat = 0; error_code = 0; stack_trace = ""; }
    public ProtocolException( int accept_stat, int error_code, String stack_trace ) { this.accept_stat = accept_stat; this.error_code = error_code; this.stack_trace = stack_trace; }
    public ProtocolException( Object from_hash_map ) { accept_stat = 0; error_code = 0; stack_trace = ""; this.deserialize( from_hash_map ); }
    public ProtocolException( Object[] from_array ) { accept_stat = 0; error_code = 0; stack_trace = "";this.deserialize( from_array ); }

    public int getAccept_stat() { return accept_stat; }
    public void setAccept_stat( int accept_stat ) { this.accept_stat = accept_stat; }
    public int getError_code() { return error_code; }
    public void setError_code( int error_code ) { this.error_code = error_code; }
    public String getStack_trace() { return stack_trace; }
    public void setStack_trace( String stack_trace ) { this.stack_trace = stack_trace; }

    // Object
    public String toString()
    {
        return "ProtocolException( " + Integer.toString( accept_stat ) + ", " + Integer.toString( error_code ) + ", " + "\"" + stack_trace + "\"" + " )";
    }

    // Serializable
    public int getTag() { return 1100; }
    public String getTypeName() { return "org::xtreemfs::babudb::interfaces::ReplicationInterface::ProtocolException"; }

    public void deserialize( Object from_hash_map )
    {
        this.deserialize( ( HashMap<String, Object> )from_hash_map );
    }
        
    public void deserialize( HashMap<String, Object> from_hash_map )
    {
        this.accept_stat = ( from_hash_map.get( "accept_stat" ) instanceof Integer ) ? ( ( Integer )from_hash_map.get( "accept_stat" ) ).intValue() : ( ( Long )from_hash_map.get( "accept_stat" ) ).intValue();
        this.error_code = ( from_hash_map.get( "error_code" ) instanceof Integer ) ? ( ( Integer )from_hash_map.get( "error_code" ) ).intValue() : ( ( Long )from_hash_map.get( "error_code" ) ).intValue();
        this.stack_trace = ( String )from_hash_map.get( "stack_trace" );
    }
    
    public void deserialize( Object[] from_array )
    {
        this.accept_stat = ( from_array[0] instanceof Integer ) ? ( ( Integer )from_array[0] ).intValue() : ( ( Long )from_array[0] ).intValue();
        this.error_code = ( from_array[1] instanceof Integer ) ? ( ( Integer )from_array[1] ).intValue() : ( ( Long )from_array[1] ).intValue();
        this.stack_trace = ( String )from_array[2];        
    }

    public void deserialize( ReusableBuffer buf )
    {
        accept_stat = buf.getInt();
        error_code = buf.getInt();
        stack_trace = org.xtreemfs.babudb.interfaces.utils.XDRUtils.deserializeString( buf );
    }

    public Object serialize()
    {
        HashMap<String, Object> to_hash_map = new HashMap<String, Object>();
        to_hash_map.put( "accept_stat", new Integer( accept_stat ) );
        to_hash_map.put( "error_code", new Integer( error_code ) );
        to_hash_map.put( "stack_trace", stack_trace );
        return to_hash_map;        
    }

    public void serialize( ONCRPCBufferWriter writer ) 
    {
        writer.putInt( accept_stat );
        writer.putInt( error_code );
        org.xtreemfs.babudb.interfaces.utils.XDRUtils.serializeString( stack_trace, writer );
    }
    
    public int calculateSize()
    {
        int my_size = 0;
        my_size += ( Integer.SIZE / 8 );
        my_size += ( Integer.SIZE / 8 );
        my_size += org.xtreemfs.babudb.interfaces.utils.XDRUtils.stringLengthPadded(stack_trace);
        return my_size;
    }


    private int accept_stat;
    private int error_code;
    private String stack_trace;    

}

