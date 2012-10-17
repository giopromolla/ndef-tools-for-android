/***************************************************************************
 * 
 * This file is part of the 'NDEF Tools for Android' project at
 * http://code.google.com/p/ndef-tools-for-android/
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 ****************************************************************************/

package org.ndeftools;

import java.util.Arrays;

import org.ndeftools.externaltype.ExternalTypeRecord;
import org.ndeftools.wellknown.ActionRecord;
import org.ndeftools.wellknown.GcActionRecord;
import org.ndeftools.wellknown.GcDataRecord;
import org.ndeftools.wellknown.GcTargetRecord;
import org.ndeftools.wellknown.GenericControlRecord;
import org.ndeftools.wellknown.SignatureRecord;
import org.ndeftools.wellknown.SmartPosterRecord;
import org.ndeftools.wellknown.TextRecord;
import org.ndeftools.wellknown.UriRecord;
import org.ndeftools.wellknown.handover.AlternativeCarrierRecord;
import org.ndeftools.wellknown.handover.CollisionResolutionRecord;
import org.ndeftools.wellknown.handover.ErrorRecord;
import org.ndeftools.wellknown.handover.HandoverCarrierRecord;
import org.ndeftools.wellknown.handover.HandoverRequestRecord;
import org.ndeftools.wellknown.handover.HandoverSelectRecord;

import android.annotation.SuppressLint;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.util.Log;

/**
 * 
 * High-level representation of a {@link NdefRecord}
 * 
 * @author Thomas Rorvik Skjolberg (skjolber@gmail.com)
 *
 */

@SuppressLint("NewApi")
public abstract class Record {


    private static final byte FLAG_MB = (byte) 0x80;
    private static final byte FLAG_ME = (byte) 0x40;
    private static final byte FLAG_SR = (byte) 0x10;
    private static final byte FLAG_IL = (byte) 0x08;

	/**
	 * 
	 * Create a message with correct message begin and message end flags, intended for cases with multiple
	 * records within another record payload.
	 * 
	 */
	
    protected static void normalizeMessageBeginEnd(byte[] ndefMessage) {
    	normalizeMessageBeginEnd(ndefMessage, 0, ndefMessage.length);
    }

		
    protected static void normalizeMessageBeginEnd(byte[] ndefMessage, int offset, int length) {
    	// normalize message begin and message end messages

    	int count = offset;
    	while(count < offset + length) {
    		int headerCount = count;
    		int header = (ndefMessage[count++] & 0xff);
    		if (count >= offset + length) {
    			return; // invalid, defer error to NdefMessage parsing
    		}

    		int typeLength = (ndefMessage[count++] & 0xff);
    		if (count >= offset + length) {
    			return;  // invalid, defer error to NdefMessage parsing
    		}

    		int payloadLength;
    		if((header & FLAG_SR) != 0) {
    			payloadLength = (ndefMessage[count++] & 0xff);
    			if (count >= offset + length) {
    				return;  // invalid, defer error to NdefMessage parsing
    			}
    		} else {
    			if (count + 4 >= offset + length) {
    				return;  // invalid, defer error to NdefMessage parsing
    			}
    			payloadLength = (((ndefMessage[count] & 0xff) << 24) + ((ndefMessage[count + 1]  & 0xff) << 16) + ((ndefMessage[count + 2]  & 0xff) << 8) + ((ndefMessage[count+3]  & 0xff) << 0)); // strictly speaking this is a unsigned int

    			count += 4;
    		}

    		if((header & FLAG_IL) != 0) {
        		count += typeLength + payloadLength + (ndefMessage[count++] & 0xff);
    		} else {
        		count += typeLength + payloadLength;
    		}

    		// repair mb and me
    		if(headerCount == offset) {
    			// mb
    			header = header | FLAG_MB;
    		} else {
    			header = header & ~FLAG_MB;
    		}

    		if(count == offset + length) {
    			// me
    			header = header | FLAG_ME;
    		} else {
    			header = header & ~FLAG_ME;
    		}

			ndefMessage[headerCount] = (byte)header;
    	}
    }

	public static Record parse(NdefRecord ndefRecord) throws FormatException {
		short tnf = ndefRecord.getTnf();
		
		Record record = null;
		switch (tnf) {
        case NdefRecord.TNF_EMPTY: {
        	record = EmptyRecord.parse(ndefRecord);
        	
        	break;
        }
        case NdefRecord.TNF_WELL_KNOWN: {
        	record = parseWellKnown(ndefRecord);
        	
        	break;
        }
        case NdefRecord.TNF_MIME_MEDIA: {
        	record = MimeRecord.parse(ndefRecord);
        	
        	break;
        }
        case NdefRecord.TNF_ABSOLUTE_URI: {
        	record = AbsoluteUriRecord.parse(ndefRecord);
        	
        	break;
        }
        case NdefRecord.TNF_EXTERNAL_TYPE: {
        	record = ExternalTypeRecord.parse(ndefRecord);

        	break;
        }
        case NdefRecord.TNF_UNKNOWN: {
        	record = UnknownRecord.parse(ndefRecord);
        	
        	break;
        }
        /*
        case NdefRecord.TNF_UNCHANGED: {
        	throw new IllegalArgumentException("Chunked records no supported"); // chunks are abstracted away by android so should never happen
        }
        */
        	
		}

		if(record == null) { // pass through
			record = UnsupportedRecord.parse(ndefRecord);
		}
		
		if(ndefRecord.getId().length > 0) {
			record.setId(ndefRecord.getId());
		}
		
		return record;
	}
	
	protected static Record parseWellKnown(NdefRecord ndefRecord) throws FormatException {
		
        // lame type search among supported types
        byte[] type = ndefRecord.getType();
        if(type.length == 1) { 
        	// uri = U
        	// text = T
        	// gctarget = t
        	// gcdata = d
        	// gcaction a
        	switch(type[0]) {
        	case 'U' : {
        		return UriRecord.parseNdefRecord(ndefRecord);
        	}
        	case 'T' : {
        		
        		return TextRecord.parseNdefRecord(ndefRecord);
        	}
        	case 't' : {
        		
        		return GcTargetRecord.parseNdefRecord(ndefRecord);
        	}
        	case 'd' : {
        		
        		return GcDataRecord.parseNdefRecord(ndefRecord);
        	}
        	case 'a' : {
        		
        		return GcActionRecord.parseNdefRecord(ndefRecord);
        	}
        	}
        	
        } else if(type.length == 2) {

        	// smartposter = Sp
        	// genericcontrol = Gc
        	// alternativecarrier = ac
        	// handovercarrier = Hc
        	// handoverselect = Hs
        	// handoverrequest = Hr
        	// collision resolution = cr

        	switch(type[0]) {
        	case 'S' : {
        		if(type[1] == 'p') {
        			return SmartPosterRecord.parseNdefRecord(ndefRecord);
        		}
        	}
        	case 'G' : {
        		if(type[1] == 'c') {
        			return GenericControlRecord.parseNdefRecord(ndefRecord);
        		}
        	}
        	case 'a' : {
        		
        		if(type[1] == 'c') {
        			return AlternativeCarrierRecord.parseNdefRecord(ndefRecord);
        		}
        	}
        	case 'c' : {
        		
        		if(type[1] == 'r') {
        			return CollisionResolutionRecord.parseNdefRecord(ndefRecord);
        		}
        	}
        	case 'H' : {
        		if(type[1] == 'c') {
        			return HandoverCarrierRecord.parseNdefRecord(ndefRecord);
        		} else if(type[1] == 's') {
            		return HandoverSelectRecord.parseNdefRecord(ndefRecord);
        		} else if(type[1] == 'r') {
            		return HandoverRequestRecord.parseNdefRecord(ndefRecord);
            	}
        	}
        	}
        	
        } else if(type.length == 3) {
        	// action = act
        	// error = err
        	// signature = Sig
        	if(type[0] == 'a' && type[1] == 'c' && type[2] == 't') {
        		return ActionRecord.parseNdefRecord(ndefRecord);
        	} else if(type[0] == 'e' && type[1] == 'r' && type[2] == 'r') {
        		return ErrorRecord.parseNdefRecord(ndefRecord);
        	} else if(type[0] == 'S' && type[1] == 'i' && type[2] == 'g') {
        		return SignatureRecord.parseNdefRecord(ndefRecord);
        	} 
        }
        
        return null;
        
	}
	
	protected static Record parse(byte[] record) throws FormatException {
		NdefMessage message = new NdefMessage(record);
		if(message.getRecords().length != 1) {
			throw new IllegalArgumentException();
		}
		return Record.parse(message.getRecords()[0]);
	}
	
	protected static Record parse(byte[] record, int offset, int length) throws FormatException {
		byte[] recordsPayload = new byte[length];
		System.arraycopy(record, offset, recordsPayload, 0, length);
		
		return parse(recordsPayload);
	}	
	
	protected byte[] id = null;

	public byte[] getId() {
		return id;
	}

	public void setId(byte[] id) {
		this.id = id;
	}

	public void setKey(String key) {
		this.id = key.getBytes();
	}

	public String getKey() {
		return id == null ? null : new String(id);
	}

	public boolean hasKey() {
		return id != null && id.length > 0;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(id);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Record other = (Record)obj;
		if (!Arrays.equals(id, other.id))
			return false;
		return true;
	}

	public abstract NdefRecord getNdefRecord();

	public byte[] toByteArray() {
		if (android.os.Build.VERSION.SDK_INT >= 16) {
			return new NdefMessage(getNdefRecord()).toByteArray();
		} else {
			return new NdefMessage(new NdefRecord[]{getNdefRecord()}).toByteArray();
		}
	}

}