package com.oracle.truffle.espresso.nodes.quick.invoke;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.bytecodes.InvokeInterface;
import com.oracle.truffle.espresso.nodes.bytecodes.InvokeInterfaceNodeGen;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class InvokeCallAllBindingsQuickNode extends InvokeQuickNode {

    @Child InvokeInterface.WithoutNullCheck invokeInterface;

    public InvokeCallAllBindingsQuickNode(Method method, int top, int curBCI) {
        super(method, top, curBCI);
        this.invokeInterface = insert(InvokeInterfaceNodeGen.WithoutNullCheckNodeGen.create(method));
    }

    @Override
    public int execute(VirtualFrame frame) {
        Object[] args = getArguments(frame);
        nullCheck((StaticObject) args[0]);
        return pushResult(frame, invokeInterface.execute(args));
    }
}
