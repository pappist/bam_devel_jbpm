/**
 * Copyright 2010 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jbpm.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;

@Entity
public class Content implements Externalizable {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long   id;
    
    @Lob
    private byte[] content;
    
    public Content() {
        
    }

    public Content(byte[] content) {
        this.content = content;
    }
    
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeLong( id );
        out.writeInt( content.length );
        out.write( content );        
    }
    
    public void readExternal(ObjectInput in) throws IOException,
                                            ClassNotFoundException {
        id = in.readLong();
        content = new byte[ in.readInt() ];
        in.readFully( content );
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode( content );
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if ( this == obj ) return true;
        if ( obj == null ) return false;
        if ( !(obj instanceof Content) ) return false;
        Content other = (Content) obj;
        if ( !Arrays.equals( content,
                             other.content ) ) return false;
        return true;
    }
        
}
