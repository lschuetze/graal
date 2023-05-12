package com.oracle.truffle.espresso.nodes.quick.invoke;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.bytecodes.InvokeCallNext;
import com.oracle.truffle.espresso.nodes.bytecodes.InvokeCallNextNodeGen;


public class InvokeCallNextQuickNode extends InvokeQuickNode {

    @Child InvokeCallNext callAllBindings;

    final Method baseMethod;

    public InvokeCallNextQuickNode(Method callNext, Method baseMethod, int top, int curBCI) {
        super(callNext, top, curBCI);
        this.baseMethod = baseMethod;
        this.callAllBindings = insert(InvokeCallNextNodeGen.create(baseMethod));
    }

    @Override
    public int execute(VirtualFrame frame) {
        Object[] args = getArguments(frame);

        Object result = callAllBindings.execute(args);
        return pushResult(frame, result);
    }
}
