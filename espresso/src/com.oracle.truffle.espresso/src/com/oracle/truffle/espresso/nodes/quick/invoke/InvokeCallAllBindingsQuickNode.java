package com.oracle.truffle.espresso.nodes.quick.invoke;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.nodes.bytecodes.InvokeCallAllBindings;
import com.oracle.truffle.espresso.nodes.bytecodes.InvokeCallAllBindingsNodeGen;

public final class InvokeCallAllBindingsQuickNode extends InvokeQuickNode {

    final Assumption mustSetFrame = Truffle.getRuntime().createAssumption();

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

        //if (mustSetFrame.isValid()) {
            // TODO Lars: Is this safe to assume?
        EspressoRootNode rootNode = (EspressoRootNode) getRootNode();
        if (rootNode != null) {
            rootNode.setFrameBaseMethod(frame, baseMethod);
        }
        //mustSetFrame.invalidate();
        Object result = callAllBindings.execute(args);
        return pushResult(frame, result);
    }
}
