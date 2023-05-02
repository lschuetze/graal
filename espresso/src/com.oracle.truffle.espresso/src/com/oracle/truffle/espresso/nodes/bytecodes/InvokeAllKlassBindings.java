package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.CompilerDirectives;
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

@NodeInfo(shortName = "INVOKEALLKlASSBINDINGS")
public abstract class InvokeAllKlassBindings extends EspressoNode {

    @Child InvokeBeforeCallins beforeCallins;
    @Child InvokeReplaceCallin replaceCallin;

    final ObjectKlass teamKlass;
    final Method baseMethod;

    @CompilerDirectives.CompilationFinal(dimensions = 1) //
    final MultiBinding[] bindings;

    InvokeAllKlassBindings(ObjectKlass teamKlass, Method baseMethod) {
        this.teamKlass = teamKlass;
        this.baseMethod = baseMethod;
        this.bindings = getBindings(baseMethod, teamKlass);
        this.beforeCallins = InvokeBeforeCallinsNodeGen.create(teamKlass, baseMethod, bindings);
        this.replaceCallin = InvokeReplaceCallinNodeGen.create(teamKlass, baseMethod, bindings);
    }

    public abstract Object execute(Object[] args, StaticObject teams, int index);

    @Specialization
    Object callAllBindings(Object[] args, StaticObject teams, int index) {

            //(VirtualFrame frame, StaticObject boundBase, StaticObject /* ITeam[] */ teams, int index,
            //               StaticObject /* int[] */ callinIds, int boundMethodId,
            //               StaticObject /* Object[] */ originalArgs) {
        // TODO Lars: Extra slot to store the progress on the index after each before/replace/after?
        beforeCallins.execute(args, index, teams);
        Object result = replaceCallin.execute(args, index + beforeCallins.length, teams);
        // TODO Lars: After callins
        return result;
    }

    static MultiBinding[] getBindings(Method baseMethod, ObjectKlass teamKlass) {
         /*
        // ordering strategy for callin bindings:
		// - first criterion: callinModifier: before/after have higher priority than replace.
		// - second criterion: precedence (only relevant among callins of the same callin modifier).
		// the set AbstractTeam.bindings is sorted low-to-high.
		// then TeamManager.handleTeamStateChange processes all bindings from low-to-high
		// inserting each at the front of the list of active teams, such that last added
		// will indeed have highest priority, which is in line with ordering by activation time.
         */
        ArrayList<MultiBinding> results = new ArrayList<>();
        CallinBindingsAttribute cba = (CallinBindingsAttribute) teamKlass.getAttribute(Symbol.Name.CallinBindings);
        for(MultiBinding binding : cba.getCallinBindings()) {
            Symbol<Symbol.Name> className = teamKlass.getConstantPool().symbolAt(binding.getBaseClassNameIndex());
            if (baseMethod.getDeclaringKlass().getName().equals(className)) {
                for(MultiBinding.BindingInfo info : binding.getBindingInfo()) {
                    Symbol<Symbol.Name> baseMethodName = teamKlass.getConstantPool()
                            .symbolAt(info.getBaseMethodNameIndex());
                    Symbol<Symbol.Signature> baseMethodSignature = teamKlass.getConstantPool()
                            .symbolAt(info.getBaseMethodSignatureIndex());

                    if (baseMethod.getName().equals(baseMethodName)
                            && baseMethod.getRawSignature().equals(baseMethodSignature)) {
                        results.add(binding);
                    }
                }
            }
        }
        return results.toArray(MultiBinding.EMPTY);
    }

    static int getBoundMethodId(Object[] args) {
        Integer value = (Integer) args[5];
        return value.intValue();
    }
}
