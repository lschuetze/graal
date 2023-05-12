package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.espresso.classfile.attributes.CallinBindingsAttribute;
import com.oracle.truffle.espresso.classfile.attributes.CallinBindingsAttribute.MultiBinding;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.runtime.StaticObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.TreeSet;

import static com.oracle.truffle.api.CompilerDirectives.*;

@NodeInfo(shortName = "INVOKEALLKlASSBINDINGS")
public abstract class InvokeAllKlassBindings extends InvokeCallinNode {

    final Assumption hasBeforeCallins = Truffle.getRuntime().createAssumption();
    final Assumption hasReplaceCallins = Truffle.getRuntime().createAssumption();
    final Assumption hasAfterCallins = Truffle.getRuntime().createAssumption();

    @Child InvokeBeforeCallins beforeCallins;
    @Child InvokeReplaceCallin replaceCallin;
    @Child InvokeAfterCallins afterCallins;
    @Child InvokeVirtual.WithoutNullCheck callOrig;

    final ObjectKlass teamKlass;
    final Method baseMethod;

    @CompilationFinal(dimensions = 1) //
    final MultiBinding[] bindings;

    final boolean callNext;

    InvokeAllKlassBindings(ObjectKlass teamKlass, Method baseMethod, MultiBinding[] bindings, boolean callNext) {
        this.teamKlass = teamKlass;
        this.baseMethod = baseMethod;
        this.bindings = bindings;
        this.beforeCallins = InvokeBeforeCallinsNodeGen.create(teamKlass, baseMethod, bindings);
        this.replaceCallin = InvokeReplaceCallinNodeGen.create(teamKlass, baseMethod, bindings);
        this.afterCallins = InvokeAfterCallinsNodeGen.create(teamKlass, baseMethod, bindings);
        this.callOrig = createOrig(baseMethod.getDeclaringKlass());

        if (this.beforeCallins.length == 0) {
            this.hasBeforeCallins.invalidate();
        }
        if (this.replaceCallin.length == 0) {
            this.hasReplaceCallins.invalidate();
        }
        if (this.afterCallins.length == 0) {
            this.hasAfterCallins.invalidate();
        }

        this.callNext = callNext;
    }

    /**
     * The calls are evaluated according to the following code:
     * <code>
     *     public Object _OT$callAllBindings(IBoundBase2 baze, ITeam[] teams,int idx,int[] callinIds, int boundMethodId, Object[] args)
     *     {
     *         Object res = null;
     *
     *         if ((boundMethodId & 0x80000000) == 0) { // bit 0x80000000 signals ctor (which has no before/replace bindings)
     *             this._OT$callBefore(baze, callinIds[idx], boundMethodId, args);
     *
     *             res = this._OT$callReplace(baze, teams, idx, callinIds, boundMethodId, args);
     *         }
     *
     *         this._OT$callAfter(baze, callinIds[idx], boundMethodId, args, res); // make result available to param mappings!
     *
     *         return res;
     *     }
     * </code>
     **/
    public abstract Object execute(Object[] args, StaticObject teams);

    @Specialization
    Object callAllBindings(Object[] args, StaticObject teams) {
        Object result = null;

        // TODO Lars: Specialize over length or use Assumptions? This is known statically.
        Boolean executedBefores = Boolean.FALSE;
        if (hasBeforeCallins.isValid()) {
            executedBefores = (Boolean) beforeCallins.execute(args, teams);
            if (executedBefores.booleanValue()) {
                args[3] = getIndex(args) + beforeCallins.length;
            }
        }
        // Store the current index to reset the value when we return from replace callins
        // This allows us that we do not have to rewrite the current sorting that would impact
        // the callinIds[] AND the teams[] as both arrays are indexed synchronously
        // This might see a fix when these data structures will be managed from within the VM.
        if (hasReplaceCallins.isValid()) {
            // TODO Lars: Install branch predictor?
            if (callNext && replaceCallin.hasPrecedence.isValid() && !executedBefores.booleanValue()) {
                result = replaceCallin.execute(args, teams);
            } else {
                final int index = getIndex(args);
                args[3] = index + afterCallins.length;
                result = replaceCallin.execute(args, teams);
                args[3] = index;
            }
        } else {
            // TODO Lars: this needs yet to be tested
            Object[] newArgs = new Object[] {args[0], args[5], args[6]};
            result = callOrig.execute(newArgs);
        }

        if (hasAfterCallins.isValid()) {
            afterCallins.execute(args, teams);
        }

        return result;
    }
}
