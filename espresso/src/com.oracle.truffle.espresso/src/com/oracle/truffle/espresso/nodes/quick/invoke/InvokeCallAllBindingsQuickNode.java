package com.oracle.truffle.espresso.nodes.quick.invoke;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.EspressoFrame;
import com.oracle.truffle.espresso.nodes.bytecodes.InvokeCallAllBindings;
import com.oracle.truffle.espresso.nodes.bytecodes.InvokeCallAllBindingsNodeGen;

public final class InvokeCallAllBindingsQuickNode extends InvokeQuickNode {

    @Child InvokeCallAllBindings callAllBindings;
    // TODO Lars: We will need the joinpoint ID to handle subsequent callNext
    private final Method baseMethod;

    public InvokeCallAllBindingsQuickNode(Method callAllBindings, Method baseMethod, int top, int curBCI) {
        super(callAllBindings, top, curBCI);
        this.baseMethod = baseMethod;
        this.callAllBindings = insert(InvokeCallAllBindingsNodeGen.create(baseMethod));
    }


    /**
     * Executes a contextual role call.
     *
     * The code is originally called like this:
     * <code>
     *   ITeam[] teams = TeamManager.getTeams(joinpointId);
     *   int[] callinIds = TeamManager.getCallinIds(joinpointId);
     *   Object[] args = {arg1, ... , argn};
     *   return this.callAllBindingsTruffle(this, teams, 0, callinIds, boundMethodId, args)
     * </code>
     *
     *
     **/
    @Override
    public int execute(VirtualFrame frame) {
        Object[] args = getArguments(frame);
        Object result = callAllBindings.execute(args);
        return pushResult(frame, result);
    }
}
