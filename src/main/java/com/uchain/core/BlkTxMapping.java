package com.uchain.core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.uchain.common.Serializable;
import com.uchain.common.Serializabler;
import com.uchain.crypto.UInt256;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BlkTxMapping implements Serializable{

	private UInt256 blkId;
	private List<UInt256> txIds;

	public BlkTxMapping(UInt256 blkId, List<UInt256> txIds) {
		this.blkId = blkId;
		this.txIds = txIds;
	}

	public void serialize(DataOutputStream os) {
		Serializabler.write(os, blkId);
		Serializabler.writeSeq(os,txIds);
	}
	
	public static BlkTxMapping deserialize(DataInputStream is) throws IOException {
		return new BlkTxMapping(UInt256.deserialize(is), readSeq(is));
	}

	public static ArrayList<UInt256> readSeq(DataInputStream is) throws IOException {
		int size = is.readInt();
		ArrayList<UInt256> uInt256 = new ArrayList<UInt256>(size);
		for(int i = 0; i < size; i++){
			uInt256.add(UInt256.deserialize(is));
		}
		return uInt256;
	}
}