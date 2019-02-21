package com.uchain.uvm.program;


import com.uchain.util.ByteUtil;
import com.uchain.util.RLP;
import com.uchain.util.RLPList;
import com.uchain.uvm.OpCode;

import java.util.HashSet;
import java.util.Set;

public class ProgramPrecompile {
    private static final int version = 1;

    private Set<Integer> jumpdest = new HashSet<>();

    public byte[] serialize() {
        byte[][] jdBytes = new byte[jumpdest.size() + 1][];
        int cnt = 0;
        jdBytes[cnt++] = RLP.encodeInt(version);
        for (Integer dst : jumpdest) {
            jdBytes[cnt++] = RLP.encodeInt(dst);
        }

        return RLP.encodeList(jdBytes);
    }

    public static ProgramPrecompile deserialize(byte[] stream) {
        RLPList l = (RLPList) RLP.decode2(stream).get(0);
        int ver = ByteUtil.byteArrayToInt(l.get(0).getRLPData());
        if (ver != version) return null;
        ProgramPrecompile ret = new ProgramPrecompile();
        for (int i = 1; i < l.size(); i++) {
            ret.jumpdest.add(ByteUtil.byteArrayToInt(l.get(i).getRLPData()));
        }
        return ret;
    }

    public static ProgramPrecompile compile(byte[] ops) {
        ProgramPrecompile ret = new ProgramPrecompile();
        for (int i = 0; i < ops.length; ++i) {

            OpCode op = OpCode.code(ops[i]);
            if (op == null) continue;

            if (op.equals(OpCode.JUMPDEST)) ret.jumpdest.add(i);

            if (op.asInt() >= OpCode.PUSH1.asInt() && op.asInt() <= OpCode.PUSH32.asInt()) {
                i += op.asInt() - OpCode.PUSH1.asInt() + 1;
            }
        }
        return ret;
    }

    public boolean hasJumpDest(int pc) {
        return jumpdest.contains(pc);
    }

    public static void main(String[] args) throws Exception {
        ProgramPrecompile pp = new ProgramPrecompile();
        pp.jumpdest.add(100);
        pp.jumpdest.add(200);
        byte[] bytes = pp.serialize();

        ProgramPrecompile pp1 = ProgramPrecompile.deserialize(bytes);
        System.out.println(pp1.jumpdest);
    }
}