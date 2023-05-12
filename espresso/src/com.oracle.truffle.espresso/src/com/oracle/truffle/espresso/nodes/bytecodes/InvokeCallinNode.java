package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.attributes.CallinBindingsAttribute;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.runtime.StaticObject;

import java.util.ArrayList;

import static com.oracle.truffle.espresso.classfile.attributes.CallinBindingsAttribute.*;

public class InvokeCallinNode extends EspressoNode {

    static StaticObject getBoundBase(Object[] args) {
        return (StaticObject) args[1];
    }

    static StaticObject getCallinIds(Object[] args) {
        return (StaticObject) args[4];
    }

    static StaticObject getOriginalArgs(Object[] args) {
        return (StaticObject) args[6];
    }

    static int getBoundMethodId(Object[] args) {
        return ((Integer) args[5]).intValue();
    }

    static StaticObject getTeam(StaticObject teams, EspressoLanguage language, int index) {
        return teams.get(language, index);
    }

    static StaticObject getTeams(Object[] args) {
        return (StaticObject) args[2];
    }

    static ObjectKlass getTeamKlass(StaticObject teams, EspressoLanguage lang, int index) {
        StaticObject team = teams.get(lang, index);
        ObjectKlass teamKlass = (ObjectKlass) team.getKlass();
        return teamKlass;
    }

    static int getIndex(Object[] args) {
        return ((Integer) args[3]).intValue();
    }

    static int getCallinId(StaticObject callinIds, EspressoLanguage language, int index) {
        return callinIds.<int[]>unwrap(language)[index];
    }

    static InvokeVirtual.WithoutNullCheck createOrig(ObjectKlass origKlass) {
        Method method = Utils.lookupOrig(origKlass);
        return InvokeVirtualNodeGen.WithoutNullCheckNodeGen.create(method);
    }

    static InvokeVirtual.WithoutNullCheck createLifting(ObjectKlass teamKlass, MultiBinding binding) {
        Method liftMethod = Utils.lookupLiftMethod(teamKlass, binding.getRoleClassNameIndex());
        return InvokeVirtualNodeGen.WithoutNullCheckNodeGen.create(liftMethod);
    }

    @CompilerDirectives.TruffleBoundary
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
        //Collection<MultiBinding> set = new TreeSet<>();
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
}
