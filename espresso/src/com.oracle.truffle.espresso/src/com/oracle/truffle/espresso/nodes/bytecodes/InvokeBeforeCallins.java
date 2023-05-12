package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.attributes.CallinBindingsAttribute;
import com.oracle.truffle.espresso.classfile.attributes.CallinBindingsAttribute.MultiBinding;
import com.oracle.truffle.espresso.descriptors.Names;
import com.oracle.truffle.espresso.descriptors.StaticSymbols;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.runtime.StaticObject;

import static com.oracle.truffle.espresso.descriptors.Symbol.*;

@NodeInfo(shortName = "INVOKEBEFORECALLINS")
public abstract class InvokeBeforeCallins extends InvokeCallinNode {

    final ObjectKlass teamKlass;
    final Method baseMethod;

    @CompilerDirectives.CompilationFinal(dimensions = 1) //
    final MultiBinding[] bindings;

    final int length;

    @CompilerDirectives.CompilationFinal(dimensions = 1 ) //
    final int[] scatterIndeces;

    InvokeBeforeCallins(ObjectKlass teamKlass, Method baseMethod, MultiBinding[] bindings) {
        this.teamKlass = teamKlass;
        this.baseMethod = baseMethod;
        this.bindings = bindings;
        // Over approximate the length
        this.scatterIndeces = new int[bindings.length];
        int count = 0;
        for(int i = 0; i < bindings.length; i++) {
            MultiBinding mb = bindings[i];
            if (mb.getCallinModifier() == CallinBindingsAttribute.KIND_BEFORE) {
                count++;
            } else {
                scatterIndeces[count]++;
            }
        }

        this.length = count;

        for (int i = 1; i < count; i++) {
            scatterIndeces[i] += scatterIndeces[i - 1];
        }
    }

    public abstract Object execute(Object[] args, StaticObject teams);
            //StaticObject /* IBoundBase2 */ boundBase, int offset,
            //                       StaticObject /* ITeam[] */ teams, StaticObject callinIds,
            //                       StaticObject /* Object[] */ originalArgs);

    @ExplodeLoop
    @Specialization
    Object doAllBefores(Object[] args, StaticObject teams,
                        @Bind("getIndex(args)") int index,
                        @Bind("getCallinIds(args)") StaticObject callinIds,
                        @Bind("getOriginalArgs(args)") StaticObject originalArgs,
                        @Bind("getBoundBase(args)") StaticObject boundBase) {

            //StaticObject /* IBoundBase2 */ boundBase, int offset,
            //            StaticObject /* ITeam[] */ teams, StaticObject callinIds,
            //            StaticObject /* Object[] */ originalArgs) {
        // TODO Lars: Compute and @Cache Binding[] for length and iterate over that
        /*
         * The int[] callinIds is actually already sorted before/after/replace.
         * We already know the team klass, we know the bindings and should know
         * their precedence and order. We must not compute this at this point anymore.
         */
        for (int i = 0; i < length; i++) {
            // TODO Lars: get rid of this access
            final int updatedIndex = scatterIndeces[i] + index;
            StaticObject team = teams.get(getLanguage(), updatedIndex);
            final int callinId = callinIds.<int[]>unwrap(getLanguage())[updatedIndex];
            MultiBinding binding = Utils.getBindingForId(bindings, callinId, CallinBindingsAttribute.KIND_BEFORE);

            if (binding == null) {
                // This can happen when 1) we have multiple callins and dispatch over two replace bindings
                // or 2) there is a bug in the index calculation
                return Boolean.FALSE;
            }

            // TODO Lars: Lift and RoleCalls own AST nodes?
            final int roleClassNameIndex = binding.getRoleClassNameIndex();
            Method liftMethod = Utils.lookupLiftMethod(teamKlass, roleClassNameIndex);
            DirectCallNode lift = DirectCallNode.create(liftMethod.getCallTarget());
            StaticObject roleObject = (StaticObject) lift.call(team, boundBase);

            Method roleMethod = Utils.lookupRoleMethod(teamKlass, (ObjectKlass) roleObject.getKlass(), binding);
            DirectCallNode roleNode = DirectCallNode.create(roleMethod.getCallTarget());
            Object[] roleCallArgs = null;
            //BEFORE = 1, REPLACE = 2, AFTER = 3
            roleCallArgs = new Object[originalArgs.length(getLanguage()) + 1];
            roleCallArgs[0] = roleObject;
            for (int l = 0; l < originalArgs.length(getLanguage()); l++) {
                StaticObject obj = originalArgs.get(getLanguage(), l);
                roleCallArgs[l + 1] = getMeta().unboxFloat(obj);
            }
            roleNode.call(roleCallArgs);
        }
        return Boolean.TRUE;
    }
}
